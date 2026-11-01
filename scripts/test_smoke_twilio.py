import json
import base64
import hashlib
import threading
import unittest
from contextlib import redirect_stdout
from io import StringIO
from types import SimpleNamespace
from unittest.mock import MagicMock, patch
from urllib.parse import parse_qs, urlencode
from urllib.request import Request, urlopen
from uuid import UUID

from smoke_twilio import CaptureServer, run, signature


class SmokeTwilioTest(unittest.TestCase):
    def exercise_run(self, *, status=200, elapsed=0.01, echo=True,
                     destination='whatsapp:+15551234567', body='Perfil QA-Smoke', duplicate=False, capture=True):
        args = SimpleNamespace(sender='whatsapp:+15551234567', body='Consultar perfil',
                               gateway='http://localhost:8090', expect='qa-smoke', timeout=0.01, observe=0.01)
        requests = []
        with CaptureServer(('127.0.0.1', 0)) as server:
            def send(request, timeout):
                requests.append(request)
                if len(requests) == 2 and capture:
                    server.captures.append({'To': destination, 'Body': body, 'sid': 'SM' + '0' * 32})
                    if duplicate:
                        server.captures.append(dict(server.captures[0]))
                response = SimpleNamespace(status=status, headers={
                    'correlationId': request.get_header('Correlationid') if echo else 'wrong'})
                context = MagicMock()
                context.__enter__.return_value = response
                return context

            output = StringIO()
            with patch.dict('os.environ', {'TWILIO_AUTH_TOKEN': 'synthetic-token',
                                          'TWILIO_WEBHOOK_PUBLIC_URL': 'https://bank.example/webhook/twilio',
                                          'TWILIO_WHATSAPP_FROM': 'whatsapp:+15005550006'}), \
                    patch('smoke_twilio.urlopen', side_effect=send), \
                    patch('smoke_twilio.time.monotonic', side_effect=[0, elapsed, 1, 1 + elapsed]), \
                    redirect_stdout(output):
                run(args, server)
            return requests, json.loads(output.getvalue())

    def test_run_signs_same_message_for_replay_and_reports_partial_evidence(self):
        requests, result = self.exercise_run()
        self.assertEqual(len(requests), 2)
        self.assertEqual(requests[0].data, requests[1].data)
        self.assertEqual(requests[0].headers, requests[1].headers)
        params = {key: values[0] for key, values in parse_qs(requests[0].data.decode()).items()}
        self.assertEqual(requests[0].full_url, 'http://localhost:8090/webhook/twilio')
        self.assertEqual(requests[0].get_header('X-twilio-signature'),
                         signature('https://bank.example/webhook/twilio', params, 'synthetic-token'))
        self.assertEqual(result['messageId'], params['MessageSid'])
        self.assertEqual(result['correlationId'], requests[0].get_header('Correlationid'))
        self.assertEqual(str(UUID(result['correlationId'])), result['correlationId'])
        self.assertEqual(result['conversationId'], hashlib.sha256(
            (params['From'] + '\n' + params['To']).encode()).hexdigest())
        self.assertRegex(params['MessageSid'], r'^SM[0-9a-f]{32}$')
        _, next_result = self.exercise_run()
        self.assertNotEqual(next_result['messageId'], result['messageId'])
        self.assertNotEqual(next_result['correlationId'], result['correlationId'])
        self.assertEqual(result['transport'], 'ok')
        self.assertIn('NO VERIFICADO', result['e2e'])

    def test_run_rejects_bad_ack_status_latency_or_correlation(self):
        for options, message in [({'status': 202}, 'ACK inválido'),
                                 ({'elapsed': 1}, 'ACK inválido'),
                                 ({'echo': False}, 'Correlación no conservada')]:
            with self.subTest(options=options), self.assertRaisesRegex(RuntimeError, message):
                self.exercise_run(**options)

    def test_run_rejects_wrong_destination_body_or_duplicate(self):
        for options, message in [({'destination': 'whatsapp:+15550000000'}, 'Destino/cuerpo'),
                                 ({'body': 'Contingencia'}, 'Destino/cuerpo'),
                                 ({'duplicate': True}, 'Segundo envío')]:
            with self.subTest(options=options), self.assertRaisesRegex(RuntimeError, message):
                self.exercise_run(**options)

    def test_run_fails_when_no_outbound_arrives_before_deadline(self):
        with self.assertRaisesRegex(RuntimeError, 'Sin outbound dentro del plazo'):
            self.exercise_run(capture=False)

    def test_signature_matches_rfc2202_hmac_sha1_vector(self):
        self.assertEqual(base64.b64decode(signature('Hi There', {}, '\x0b' * 20)).hex(),
                         'b617318655057264e28bc0b6fb378c8ef146be00')

    def test_signature_sorts_form_fields(self):
        self.assertEqual(signature('url', {'Z': '2', 'A': '1'}, 'token'),
                         signature('urlA1Z2', {}, 'token'))

    def test_simulator_captures_sdk_form_and_returns_unique_sids(self):
        with CaptureServer(('127.0.0.1', 0)) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                sids = []
                for _ in range(2):
                    request = Request(f'http://127.0.0.1:{server.server_port}/2010-04-01/'
                                      'Accounts/AC00000000000000000000000000000000/Messages.json',
                                      data=urlencode({'From': 'whatsapp:+15005550006',
                                                      'To': 'whatsapp:+15551234567',
                                                      'Body': 'QA-Smoke'}).encode())
                    with urlopen(request, timeout=2) as response:
                        self.assertEqual(response.status, 201)
                        sids.append(json.load(response)['sid'])
                self.assertEqual(len(set(sids)), 2)
                self.assertEqual(server.captures[0]['Body'], 'QA-Smoke')
            finally:
                server.shutdown()
                thread.join(timeout=2)
