"""Toy module for exercising Marginalis margin notes."""


def fib(n: int) -> int:
    """Return the n-th Fibonacci number (0-indexed)."""
    if n < 0:
        raise ValueError(f"n must be non-negative, got {n}")
    if n < 2:
        return n
    prev, curr = 0, 1

    for _ in range(n - 1):
        prev, curr = curr, prev + curr
    return curr


def fib_sequence(count: int) -> list[int]:
    return [fib(i) for i in range(count)]


if __name__ == "__main__":
    print(fib_sequence(10))
