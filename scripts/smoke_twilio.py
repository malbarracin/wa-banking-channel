"""Smoke de transporte local; no acredita por sí solo H9/H7/negocio ni ausencia de DLQ."""
import argparse
import base64
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import re
import threading
import time
from urllib.parse import parse_qs, urlencode
from urllib.request import Request, urlopen
from uuid import uuid4


class CaptureServer(ThreadingHTTPServer):
    def __init__(self, address):
        super().__init__(address, CaptureHandler)
        self.captures = []
        self.condition = threading.Condition()


class CaptureHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # No registrar Authorization ni formularios.

    def do_POST(self):
        if not re.fullmatch(r'/2010-04-01/Accounts/AC[0-9a-fA-F]{32}/Messages.json', self.path):
            self.send_error(404)
            return
        length = int(self.headers.get('Content-Length', '0'))
        if not 0 < length <= 65536:
            self.send_error(413)
            return
        form = parse_qs(self.rfile.read(length).decode(), strict_parsing=True)
        if not all(len(form.get(key, [])) == 1 for key in ('To', 'From', 'Body')):
            self.send_error(400)
            return
        sid = 'SM' + uuid4().hex
        with self.server.condition:
            self.server.captures.append({**{k: v[0] for k, v in form.items()}, 'sid': sid})
            self.server.condition.notify_all()
        body = json.dumps({'sid': sid, 'status': 'queued'}).encode()
        self.send_response(201)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def signature(url, params, token):
    value = url + ''.join(key + params[key] for key in sorted(params))
    return base64.b64encode(hmac.new(token.encode(), value.encode(), hashlib.sha1).digest()).decode()


def run(args, server):
    correlation = str(uuid4())
    params = {'MessageSid': 'SM' + uuid4().hex, 'From': args.sender,
              'To': os.environ['TWILIO_WHATSAPP_FROM'], 'Body': args.body}
    token = os.environ['TWILIO_AUTH_TOKEN']
    public_url = os.environ['TWILIO_WEBHOOK_PUBLIC_URL']
    headers = {'X-Twilio-Signature': signature(public_url, params, token),
               'correlationId': correlation}

    def send():
        started = time.monotonic()
        with urlopen(Request(args.gateway + '/webhook/twilio', data=urlencode(params).encode(),
                             headers=headers), timeout=5) as response:
            if response.status != 200 or time.monotonic() - started >= 1:
                raise RuntimeError('ACK inválido o >= 1 segundo')
            if response.headers.get('correlationId') != correlation:
                raise RuntimeError('Correlación no conservada en ACK')

    send()
    send()  # Replay inmediato: no depende de la latencia del modelo.
    with server.condition:
        if not server.condition.wait_for(lambda: bool(server.captures), timeout=args.timeout):
            raise RuntimeError('Sin outbound dentro del plazo')
        capture = server.captures[0]
        if capture['To'] != args.sender or args.expect.casefold() not in capture['Body'].casefold():
            raise RuntimeError('Destino/cuerpo outbound inesperado')
    with server.condition:
        duplicate = server.condition.wait_for(lambda: len(server.captures) > 1, timeout=args.observe)
        if duplicate:
            raise RuntimeError('Segundo envío durante observación de replay')
    conversation = hashlib.sha256((params['From'] + '\n' + params['To']).encode()).hexdigest()
    print(json.dumps({'transport': 'ok', 'correlationId': correlation,
                      'conversationId': conversation, 'messageId': params['MessageSid'],
                      'outboundSid': capture['sid'], 'replayObservationSeconds': args.observe,
                      'e2e': 'NO VERIFICADO: contrastar Kafka, H9, H7 y negocio'}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=8099)
    parser.add_argument('--gateway', default='http://127.0.0.1:8090')
    parser.add_argument('--sender', required=True)
    parser.add_argument('--body', required=True)
    parser.add_argument('--expect', required=True)
    parser.add_argument('--timeout', type=float, default=60)
    parser.add_argument('--observe', type=float, default=15)
    args = parser.parse_args()
    if not re.fullmatch(r'whatsapp:\+1555[0-9]{7}', args.sender):
        parser.error('Usar exclusivamente el número sintético +1555 del seed')
    for key in ('TWILIO_AUTH_TOKEN', 'TWILIO_WEBHOOK_PUBLIC_URL', 'TWILIO_WHATSAPP_FROM', 'GOOGLE_API_KEY'):
        if not os.environ.get(key):
            parser.error('Falta prerrequisito: ' + key)
    if args.timeout <= 0 or not 0 < args.observe < 60:
        parser.error('Plazos inválidos; observación debe ser menor a 60s')
    with CaptureServer(('0.0.0.0', args.port)) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        try:
            run(args, server)
        finally:
            server.shutdown()


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, ValueError):
        raise SystemExit('Smoke fallido; revisar salud/captura sin publicar secretos') from None
