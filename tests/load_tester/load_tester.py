from service_classes import User, Product, Order
import shlex
import argparse
import asyncio
import aiohttp
import time
import statistics
from dataclasses import dataclass, field
from typing import Optional

import itertools

OBJ_MAPPING = {"USER": User, "PRODUCT": Product, "ORDER": Order}
CMD_MAPPING = {"CREATE": "create", "DELETE": "delete", "UPDATE": "update", "PLACE": "place order"}


# ---------------------------------------------------------------------------
# Payload generation (unchanged from original)
# ---------------------------------------------------------------------------

def generate_payload(line):
    match line[1]:
        case "CREATE" | "DELETE":
            obj_type = line[0]
            obj_instance = OBJ_MAPPING[obj_type](*line[2:])
            payload = {"command": CMD_MAPPING[line[1]], **obj_instance.__dict__}

        case "UPDATE":
            payload = {"command": CMD_MAPPING[line[1]], "id": int(line[2])}
            for arg in line[3:]:
                if ":" in arg:
                    key, val = arg.split(":", 1)
                    payload[key] = val

        case "GET":
            payload = {"id": int(line[2])}

        case "PLACE":
            obj_type = line[0]
            obj_instance = OBJ_MAPPING[obj_type](*line[2:])
            payload = {"command": CMD_MAPPING[line[1]], **obj_instance.__dict__}
        case _:
            payload = {}

    return payload


# ---------------------------------------------------------------------------
# Workload streaming 
# ---------------------------------------------------------------------------

def stream_workload(workload_file: str, total_requests: int):
    # 1. Read the small file into a list in memory (filtering for USER)
    base_requests = []
    with open(workload_file, 'r') as file:
        for line in file:
            parts = shlex.split(line.strip())
            if parts and parts[0] in OBJ_MAPPING:
                base_requests.append(parts)
    
    if not base_requests:
        print("Error: No valid USER requests found in the workload file.")
        return

    # 2. Create an endless loop of those base requests
    endless_cycle = itertools.cycle(base_requests)

    # 3. Yield exactly the number of requests you want to test
    for _ in range(total_requests):
        yield next(endless_cycle)
# ---------------------------------------------------------------------------
# Result tracking
# ---------------------------------------------------------------------------

@dataclass
class RequestResult:
    line: list
    latency_ms: float
    status_code: Optional[int]
    error: Optional[str] = None

    @property
    def success(self) -> bool:
        return self.error is None and self.status_code is not None and self.status_code < 400


# ---------------------------------------------------------------------------
# Async request sender
# ---------------------------------------------------------------------------

async def send_single_request_async(
    session: aiohttp.ClientSession,
    base_url: str,
    line: list,
    semaphore: asyncio.Semaphore,
) -> RequestResult:
    """Send one request and return timing + status."""
    async with semaphore:
        payload = generate_payload(line)
        url_base = f"{base_url}/{line[0].lower()}"

        try:
            if line[1] == "GET":
                url = f"{url_base}/{line[2]}"
                start = time.perf_counter()
                async with session.get(url) as response:
                    await response.text()
                    latency_ms = (time.perf_counter() - start) * 1000
                    return RequestResult(line=line, latency_ms=latency_ms, status_code=response.status)
            else:
                start = time.perf_counter()
                async with session.post(url_base, json=payload) as response:
                    await response.text()
                    latency_ms = (time.perf_counter() - start) * 1000
                    return RequestResult(line=line, latency_ms=latency_ms, status_code=response.status)

        except aiohttp.ClientError as e:
            latency_ms = (time.perf_counter() - start) * 1000
            return RequestResult(line=line, latency_ms=latency_ms, status_code=None, error=str(e))


# ---------------------------------------------------------------------------
# Metrics reporting
# ---------------------------------------------------------------------------

def print_metrics(results: list[RequestResult], total_wall_time: float):
    total = len(results)
    successes = [r for r in results if r.success]
    failures = [r for r in results if not r.success]
    latencies = [r.latency_ms for r in results]

    sorted_latencies = sorted(latencies)

    def percentile(data, pct):
        idx = int(len(data) * pct / 100)
        return data[min(idx, len(data) - 1)]

    throughput = total / total_wall_time if total_wall_time > 0 else 0

    print("\n" + "=" * 55)
    print("               LOAD TEST RESULTS")
    print("=" * 55)
    print(f"  Total requests   : {total}")
    print(f"  Successful       : {len(successes)}  ({100*len(successes)/total:.1f}%)")
    print(f"  Failed           : {len(failures)}  ({100*len(failures)/total:.1f}%)")
    print(f"  Wall time        : {total_wall_time:.2f}s")
    print(f"  Throughput       : {throughput:.1f} req/s")
    print("-" * 55)
    print("  Latency (ms)")
    print(f"    min            : {min(latencies):.1f}")
    print(f"    mean           : {statistics.mean(latencies):.1f}")
    print(f"    median (p50)   : {percentile(sorted_latencies, 50):.1f}")
    print(f"    p90            : {percentile(sorted_latencies, 90):.1f}")
    print(f"    p95            : {percentile(sorted_latencies, 95):.1f}")
    print(f"    p99            : {percentile(sorted_latencies, 99):.1f}")
    print(f"    max            : {max(latencies):.1f}")
    print("-" * 55)

    # Status code breakdown
    status_counts: dict[str, int] = {}
    for r in results:
        key = str(r.status_code) if r.status_code else f"ERROR: {r.error[:40]}"
        status_counts[key] = status_counts.get(key, 0) + 1

    print("  Status codes")
    for code, count in sorted(status_counts.items()):
        print(f"    {code:<20} : {count}")
    print("=" * 55)


# ---------------------------------------------------------------------------
# Main async runner
# ---------------------------------------------------------------------------

async def run_load_test(args):
    lines = list(stream_workload(args.workload_file, args.num_requests))
    total = len(lines)
    print(f"Generated {total} requests from {args.workload_file}")
    print(f"Concurrency cap  : {args.concurrency}")
    print(f"Target           : {args.url}")
    print(f"Firing...\n")

    semaphore = asyncio.Semaphore(args.concurrency)

    connector = aiohttp.TCPConnector(limit=args.concurrency)
    timeout = aiohttp.ClientTimeout(total=args.timeout)

    async with aiohttp.ClientSession(connector=connector, timeout=timeout) as session:
        wall_start = time.perf_counter()

        tasks = [
            send_single_request_async(session, args.url, line, semaphore)
            for line in lines
        ]

        # Fire all requests; gather preserves order and captures exceptions
        results: list[RequestResult] = await asyncio.gather(*tasks, return_exceptions=False)

        wall_time = time.perf_counter() - wall_start

    print_metrics(results, wall_time)


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Async load tester for workload files.")
    parser.add_argument('-w', dest='workload_file', required=True, help='Path to workload file')
    parser.add_argument('-u', dest='url', required=True, help='Base URL of server (e.g. http://localhost:8080)')
    parser.add_argument('-c', dest='concurrency', type=int, default=50,
                        help='Max concurrent requests in flight (default: 50)')
    parser.add_argument('--timeout', type=float, default=10.0,
                        help='Per-request timeout in seconds (default: 10)')
    parser.add_argument('-n', dest='num_requests', type=int, default=10000,
                        help='Total number of requests to generate by cycling the file (default: 10000)')
    args = parser.parse_args()

    asyncio.run(run_load_test(args))


if __name__ == "__main__":
    main()
