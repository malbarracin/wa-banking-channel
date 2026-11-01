"""Seed local H1-H3. Solo stdlib; nunca imprime ni persiste JWT o secretos."""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]
STATE = ROOT / '.local-seed' / 'smoke.json'


def api(service, path, method='GET', body=None, headers=None):
    port = os.environ.get(f'{service.upper()}_HOST_PORT',
                          {'users': '8080', 'channel': '8081', 'credentials': '8082'}[service])
    request = Request(f'http://127.0.0.1:{int(port)}{path}', method=method,
                      data=None if body is None else json.dumps(body).encode(),
                      headers={'Content-Type': 'application/json', **(headers or {})})
    try:
        with urlopen(request, timeout=20) as response:
            data = response.read()
            return json.loads(data) if data else None
    except HTTPError as error:
        # No exponer cuerpos, headers o tokens de errores del servicio.
        raise RuntimeError(f'{service}: HTTP {error.code}') from None


def issue(data):
    token = os.environ['CREDENTIAL_H3_SERVICE_TOKEN']
    result = api('credentials', '/api/v1/credenciales/emitir', 'POST', {
        'numeroWhatsapp': data['phone'], 'documentoTipo': 'DNI',
        'documentoNumero': data['document'], 'vinculoActivo': True,
        'identidadVerificada': True}, {'Authorization': f'Bearer {token}'})
    return result['credencial']


def seed():
    if STATE.exists():
        raise RuntimeError('Ya existe un seed; ejecutar check o cleanup antes de otro seed.')
    data = {'document': 'QA-' + uuid4().hex,
            'phone': '+1555' + ''.join(str(secrets.randbelow(10)) for _ in range(7))}
    STATE.parent.mkdir(exist_ok=True)
    STATE.write_text(json.dumps(data), encoding='utf-8')
    api('users', '/api/v1/users', 'POST', {
        'documento': {'tipo': 'DNI', 'numero': data['document']},
        'nombre': 'QA-Smoke', 'apellido': 'Sintetico', 'fechaNacimiento': '1990-01-01',
        'email': data['document'] + '@example.test', 'telefono': data['phone']})
    link = api('channel', '/api/v1/vinculos/terminos', 'POST',
               {'numeroWhatsapp': data['phone']})
    sql = (ROOT / 'scripts/local-init/activate-smoke-link.sql').read_text(encoding='utf-8')
    result = subprocess.run([
        'docker', 'compose', 'exec', '-T', 'channel-postgres', 'sh', '-c',
        'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"', 'seed',
        '-v', 'ON_ERROR_STOP=1', '-v', 'document=' + data['document'],
        '-v', 'phone=' + data['phone'], '-v', 'link_id=' + link['id']],
        input=sql, text=True, capture_output=True, cwd=ROOT, timeout=30)
    if result.returncode:
        raise RuntimeError('Falló la activación SQL local; revisar salud/esquema de H3.')
    check(data)


def check(data):
    state = api('channel', f"/api/v1/vinculos/{data['phone']}/estado")
    if state.get('estado') != 'ACTIVO':
        raise RuntimeError('El vínculo no está ACTIVO.')
    credential = issue(data)
    validation = api('credentials', '/api/v1/credenciales/validar', 'POST',
                     {'credencial': credential})
    if validation.get('vigente') is not True or validation.get('numeroWhatsapp') != data['phone']:
        raise RuntimeError('La credencial no es válida para el número del seed.')
    api('channel', f"/api/v1/vinculos/{data['phone']}/perfil",
        headers={'X-Canal-Credencial-Token': credential})
    print('Seed verificado: vínculo ACTIVO, credencial vigente y perfil H1 por H3.')
    print('Datos sintéticos para el smoke: ' + str(STATE))


def cleanup(data):
    credential = issue(data)
    api('channel', f"/api/v1/vinculos/{data['phone']}/desvinculacion", 'PATCH',
        {'confirmacion': True}, {'X-Canal-Credencial-Token': credential})
    # H3 revoca best-effort; confirmar explícitamente que H2 rechazó el JWT.
    try:
        api('credentials', '/api/v1/credenciales/validar', 'POST', {'credencial': credential})
    except RuntimeError as error:
        if str(error) != 'credentials: HTTP 401':
            raise
    else:
        raise RuntimeError('H2 aún acepta la credencial; completar revocación antes de limpiar H1.')
    api('users', f"/api/v1/users/DNI/{data['document']}/status", 'PATCH',
        {'estado': 'LOGICAL_DELETE'})
    STATE.unlink()
    print('Seed desvinculado, credencial revocada y cliente con baja lógica; auditoría conservada.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['seed', 'check', 'cleanup'])
    parser.add_argument('--local-only', action='store_true', required=True)
    args = parser.parse_args()
    if not os.environ.get('CREDENTIAL_H3_SERVICE_TOKEN'):
        parser.error('Definir CREDENTIAL_H3_SERVICE_TOKEN del stack local en el entorno.')
    if args.action == 'seed':
        seed()
    else:
        data = json.loads(STATE.read_text(encoding='utf-8'))
        if not data['phone'].startswith('+1555') or not data['document'].startswith('QA-'):
            raise RuntimeError('El archivo no corresponde a datos sintéticos del seed.')
        {'check': check, 'cleanup': cleanup}[args.action](data)


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, subprocess.SubprocessError) as error:
        print(f'Seed incompleto ({type(error).__name__}); conservar estado para diagnóstico. '
              'Revisar salud, tokens locales y contratos; no publicar secretos.', file=sys.stderr)
        sys.exit(1)
