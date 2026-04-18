"""Tests for ReviewConsumer readiness signal used by /ready."""
import sys
import threading
import time
import types
from unittest.mock import MagicMock


def _install_confluent_kafka_stub():
    """Minimal stub so src.consumer imports without the native confluent-kafka wheel."""
    if "confluent_kafka" in sys.modules:
        return
    mod = types.ModuleType("confluent_kafka")

    class KafkaError:
        _PARTITION_EOF = -191

    class KafkaException(Exception):
        pass

    class _Consumer:
        def __init__(self, *args, **kwargs):
            pass

        def subscribe(self, *args, **kwargs):
            pass

        def poll(self, *args, **kwargs):
            return None

        def commit(self, *args, **kwargs):
            pass

        def close(self):
            pass

    class _Producer:
        def __init__(self, *args, **kwargs):
            pass

        def produce(self, *args, **kwargs):
            pass

        def flush(self, *args, **kwargs):
            pass

    mod.Consumer = _Consumer
    mod.Producer = _Producer
    mod.KafkaError = KafkaError
    mod.KafkaException = KafkaException
    sys.modules["confluent_kafka"] = mod


_install_confluent_kafka_stub()

from src.consumer import ReviewConsumer  # noqa: E402


def test_running_false_before_start():
    consumer = ReviewConsumer(MagicMock(), MagicMock())
    assert consumer.running is False


def test_running_true_while_thread_alive():
    consumer = ReviewConsumer(MagicMock(), MagicMock())
    consumer._running = True

    def _short_run():
        time.sleep(0.2)

    consumer._thread = threading.Thread(target=_short_run)
    consumer._thread.start()
    assert consumer.running is True
    consumer._thread.join()
    assert consumer.running is False


def test_running_false_when_not_marked_running():
    consumer = ReviewConsumer(MagicMock(), MagicMock())

    def _noop():
        pass

    consumer._thread = threading.Thread(target=_noop)
    consumer._thread.start()
    consumer._thread.join()
    assert consumer._running is False
    assert consumer.running is False
