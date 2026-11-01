import json
import base64
import threading
import unittest
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from smoke_twilio import CaptureServer, signature


class SmokeTwilioTest(unittest.TestCase):
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
