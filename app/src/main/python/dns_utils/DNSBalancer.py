# MasterDnsVPN
# Author: MasterkinG32
# Github: https://github.com/masterking32
# Year: 2026

import random
from collections import defaultdict


class DNSBalancer:
    def __init__(self, resolvers: list, strategy: int):
        self.strategy = strategy
        self.rr_index = 0

        self.server_stats = defaultdict(
            lambda: {"sent": 0, "acked": 0, "rtt_sum": 0.0, "rtt_count": 0}
        )

        self._setup_strategy_dispatch()

        self.valid_servers = []
        self.valid_servers_count = 0
        self.set_balancers(resolvers)

    def _setup_strategy_dispatch(self):
        """Bind the strategy method directly to avoid branching overhead."""
        if self.strategy == 1:
            self._get_servers = self._get_servers_random
        elif self.strategy == 3:
            self._get_servers = self._get_servers_least_loss
        elif self.strategy == 4:
            self._get_servers = self._get_servers_lowest_latency
        else:
            self._get_servers = self._get_servers_round_robin

    def set_balancers(self, balancers: list):
        valid = []
        append = valid.append

        for s in balancers:
            if s.get("is_valid", False):
                if "_key" not in s:
                    s["_key"] = f"{s.get('resolver', '')}:{s.get('domain', '')}"
                append(s)

        self.resolvers = balancers
        self.valid_servers = valid
        self.valid_servers_count = len(self.valid_servers)
        self.rr_index = 0

    def _normalize_required_count(
        self, required_count: int, default_if_invalid: int = 1
    ) -> int:
        """
        Normalize caller-requested server count.
        - Non-int values fallback to default_if_invalid.
        - Values <= 0 are treated as default_if_invalid.
        - Final count is capped by available valid servers.
        """
        if not self.valid_servers_count:
            return 0

        try:
            count = int(required_count)
        except (TypeError, ValueError):
            count = int(default_if_invalid)

        if count <= 0:
            count = int(default_if_invalid)

        if count > self.valid_servers_count:
            return self.valid_servers_count
        return count

    def report_success(self, server_key: str, rtt: float = 0.0):
        stats = self.server_stats[server_key]
        stats["acked"] += 1

        if rtt > 0:
            stats["rtt_sum"] += rtt
            stats["rtt_count"] += 1

        if stats["sent"] > 1000:
            stats["sent"] = int(stats["sent"] * 0.5)
            stats["acked"] = int(stats["acked"] * 0.5)
            stats["rtt_sum"] *= 0.5
            stats["rtt_count"] = int(stats["rtt_count"] * 0.5)

    def report_send(self, server_key: str):
        self.server_stats[server_key]["sent"] += 1

    def get_loss_rate(self, server_key: str) -> float:
        stats = self.server_stats.get(server_key)
        if not stats:
            return 0.5

        sent = stats["sent"]
        if sent < 5:
            return 0.5

        loss = 1.0 - (stats["acked"] / sent)
        return max(0.0, min(1.0, loss))

    def get_avg_rtt(self, server_key: str) -> float:
        stats = self.server_stats.get(server_key)
        if not stats or stats["rtt_count"] < 5:
            return 999.0
        return stats["rtt_sum"] / stats["rtt_count"]

    def get_best_server(self):
        if not self.valid_servers_count:
            return None
        servers = self._get_servers(1)
        return servers[0] if servers else None

    def get_unique_servers(self, required_count: int) -> list:
        actual_count = self._normalize_required_count(required_count)
        if not actual_count:
            return []

        return self._get_servers(actual_count)

    # --- Strategy Methods ---
    def _get_servers_random(self, count: int) -> list:
        return random.sample(self.valid_servers, count)

    def _get_servers_least_loss(self, count: int) -> list:
        _get_loss = self.get_loss_rate
        scored = sorted(
            self.valid_servers,
            key=lambda s: _get_loss(s["_key"]),
        )
        return scored[:count]

    def _get_servers_lowest_latency(self, count: int) -> list:
        _get_rtt = self.get_avg_rtt
        scored = sorted(
            self.valid_servers,
            key=lambda s: _get_rtt(s["_key"]),
        )
        return scored[:count]

    def _get_servers_round_robin(self, count: int) -> list:
        idx = self.rr_index
        self.rr_index = (idx + count) % self.valid_servers_count

        if count == 1:
            return [self.valid_servers[idx]]

        end = idx + count
        if end <= self.valid_servers_count:
            return self.valid_servers[idx:end]
        else:
            return (
                self.valid_servers[idx:]
                + self.valid_servers[: end % self.valid_servers_count]
            )

    def get_servers_for_stream(self, stream_id: int, required_count: int) -> list:
        actual_count = self._normalize_required_count(required_count)
        if not actual_count:
            return []

        return self._get_servers(actual_count)
