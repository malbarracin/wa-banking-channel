"""Health gate cases, without requiring a running Docker daemon."""
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import json
import unittest
from unittest.mock import patch

import start_stack


def container(service, status="running", health="healthy", code=0):
    return {"Config": {"Labels": {"com.docker.compose.service": service}},
            "State": {"Status": status, "ExitCode": code,
                      "Health": {"Status": health}}}


class StartStackTest(unittest.TestCase):
    def test_success_requires_healthy_and_init_exit_zero(self):
        self.assertEqual([], start_stack.pending_services(
            ["app", "kafka-init"],
            [container("app"), container("kafka-init", "exited")]))

    def test_missing_unhealthy_starting_and_unfinished_init_wait(self):
        for containers in ([], [container("app", health="unhealthy")],
                           [container("app", health="starting")],
                           [container("app"), container("kafka-init")]):
            with self.subTest(containers=containers):
                self.assertTrue(start_stack.pending_services(["app", "kafka-init"], containers))

    def test_all_replicas_must_be_healthy(self):
        self.assertTrue(start_stack.pending_services(
            ["app"], [container("app"), container("app", health="unhealthy")]))

    def test_no_healthcheck_is_not_success(self):
        app = container("app")
        del app["State"]["Health"]
        self.assertTrue(start_stack.pending_services(["app"], [app]))

    def test_failed_init_and_stopped_permanent_service_fail(self):
        for service in (container("kafka-init", "exited", code=1),
                        container("app", "exited")):
            with self.subTest(service=service), self.assertRaises(RuntimeError):
                name = service["Config"]["Labels"]["com.docker.compose.service"]
                start_stack.pending_services([name], [service])

    def run_main(self):
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
            return start_stack.main()

    def test_successful_start(self):
        with patch.object(start_stack, "docker", side_effect=[
            "app\nkafka-init\n", "", "", "a\nb\n",
            json.dumps([container("app"), container("kafka-init", "exited")]),
        ]) as docker:
            self.assertEqual(0, self.run_main())
        self.assertEqual(("compose", "build"), docker.call_args_list[1].args)
        self.assertEqual(("compose", "up", "-d"), docker.call_args_list[2].args)
        self.assertEqual(600, docker.call_args_list[2].kwargs["timeout"])

    def test_timeout_returns_error_without_real_sleep(self):
        with patch.object(start_stack, "docker", side_effect=["app\nkafka-init", "", ""]), \
                patch.object(start_stack.time, "monotonic", side_effect=[0, 601]):
            self.assertEqual(1, self.run_main())

    def test_docker_failure_returns_error(self):
        with patch.object(start_stack, "docker", side_effect=RuntimeError("Docker falló")):
            self.assertEqual(1, self.run_main())

    def test_health_can_recover(self):
        with patch.object(start_stack, "docker", side_effect=[
            "app\nkafka-init", "", "", "a b",
            json.dumps([container("app", health="unhealthy"), container("kafka-init", "exited")]),
            "a b", json.dumps([container("app"), container("kafka-init", "exited")]),
        ]), patch.object(start_stack.time, "sleep"):
            self.assertEqual(0, self.run_main())


if __name__ == "__main__":
    unittest.main()
