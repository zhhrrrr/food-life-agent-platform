"""Bounded process-local workers with no unbounded submission queue."""
from concurrent.futures import ThreadPoolExecutor
from contextvars import copy_context
from threading import BoundedSemaphore, Lock


class ToolCapacityExceeded(RuntimeError):
    pass


class ToolAlreadyRunning(RuntimeError):
    pass


class ToolPool:
    def __init__(self, max_workers: int, *, thread_name_prefix: str = "tool-handler"):
        self._slots = BoundedSemaphore(max_workers)
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix=thread_name_prefix)
        self._lock = Lock()
        self._operations: set[str] = set()

    def submit(self, handler, operation_id: str = ""):
        inherited = copy_context()
        with self._lock:
            if operation_id and operation_id in self._operations:
                raise ToolAlreadyRunning("the original write is still in flight")
            if not self._slots.acquire(blocking=False):
                raise ToolCapacityExceeded("workers are at capacity")
            if operation_id:
                self._operations.add(operation_id)
        def run():
            try:
                return inherited.run(handler)
            finally:
                # Release before completing the Future so the next wave can use the slot.
                self._release(operation_id)
        try:
            future = self._executor.submit(run)
        except BaseException:
            self._release(operation_id)
            raise
        # A cancelled queued task never enters run(); running soft timeouts retain their slot.
        future.add_done_callback(lambda done: self._release(operation_id) if done.cancelled() else None)
        return future

    def _release(self, operation_id):
        with self._lock:
            if operation_id:
                self._operations.discard(operation_id)
            self._slots.release()

    def close(self, *, wait: bool = False):
        self._executor.shutdown(wait=wait, cancel_futures=True)


_POOLS: dict[tuple[str, int], ToolPool] = {}
_POOL_LOCK = Lock()


def get_tool_pool(max_workers: int, *, namespace: str = "tool") -> ToolPool:
    key = (namespace, max_workers)
    with _POOL_LOCK:
        if key not in _POOLS:
            _POOLS[key] = ToolPool(max_workers, thread_name_prefix=f"{namespace}-handler")
        return _POOLS[key]


def close_tool_pools():
    with _POOL_LOCK:
        pools = list(_POOLS.values())
        _POOLS.clear()
    for pool in pools:
        pool.close()
