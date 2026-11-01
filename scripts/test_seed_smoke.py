"""Contrato del arnés, sin modificar servicios ni necesitar Docker."""
import json
import unittest
from unittest.mock import patch

import seed_smoke as seed


class SeedSmokeTest(unittest.TestCase):
    def test_seed_uses_apis_before_local_sql_and_checks_profile(self):
        calls = []

        def api(service, path, method='GET', body=None, headers=None):
            calls.append((service, path, method, body))
            if path.endswith('/terminos'):
                return {'id': '00000000-0000-4000-8000-000000000001'}
            if path.endswith('/estado'):
                return {'estado': 'ACTIVO'}
            if path.endswith('/emitir'):
                return {'credencial': 'private-jwt'}
            if path.endswith('/validar'):
                return {'vigente': True, 'numeroWhatsapp': calls[1][3]['numeroWhatsapp']}

        with patch.object(seed, 'STATE') as state, \
                patch.object(seed, 'api', side_effect=api), \
                patch.dict(seed.os.environ, {'CREDENTIAL_H3_SERVICE_TOKEN': 'private-service-token'}), \
                patch.object(seed.subprocess, 'run') as docker, patch('builtins.print') as output:
            docker.return_value.returncode = 0
            state.exists.return_value = False
            seed.seed()
            stored = state.write_text.call_args.args[0]
            data = json.loads(stored)
            self.assertRegex(data['phone'], r'^\+1555\d{7}$')
            self.assertTrue(data['document'].startswith('QA-'))
            self.assertEqual(calls[0][:3], ('users', '/api/v1/users', 'POST'))
            self.assertEqual(calls[1][1], '/api/v1/vinculos/terminos')
            self.assertTrue(calls[-1][1].endswith('/perfil'))
            self.assertIn('channel-postgres', docker.call_args.args[0])
            self.assertNotIn('private-', stored + str(output.call_args_list))

    def test_inactive_link_cannot_issue_credential(self):
        with patch.object(seed, 'api', return_value={'estado': 'PENDIENTE_VERIFICACION'}) as api:
            with self.assertRaises(RuntimeError):
                seed.check({'phone': '+15555550100'})
            self.assertEqual(api.call_count, 1)

    def test_cleanup_confirms_revocation_before_deleting_user(self):
        with patch.object(seed, 'STATE') as state, \
                patch.object(seed, 'issue', return_value='private-jwt'), \
                patch.object(seed, 'api', side_effect=[{}, RuntimeError('credentials: HTTP 401'), {}]) as api, \
                patch('builtins.print'):
            seed.cleanup({'phone': '+15555550100', 'document': 'QA-test'})
            self.assertEqual(api.call_args_list[-1].args[3], {'estado': 'LOGICAL_DELETE'})
            state.unlink.assert_called_once()

    def test_cleanup_retains_state_if_revocation_did_not_happen(self):
        with patch.object(seed, 'issue', return_value='private-jwt'), \
                patch.object(seed, 'api', return_value={}) as api:
            with self.assertRaises(RuntimeError):
                seed.cleanup({'phone': '+15555550100', 'document': 'QA-test'})
            self.assertEqual(api.call_count, 2)


if __name__ == '__main__':
    unittest.main()
