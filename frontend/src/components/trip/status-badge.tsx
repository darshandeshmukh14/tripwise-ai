import { Badge } from "@/components/ui/badge";
import type { BudgetStatus, TripStatus } from "@/lib/api/types";
import { BUDGET_META, STATUS_META } from "@/lib/format";

export function StatusBadge({ status }: { status: TripStatus }) {
  const meta = STATUS_META[status];
  return <Badge variant={meta.variant}>{meta.label}</Badge>;
}

export function BudgetBadge({ status }: { status: BudgetStatus }) {
  const meta = BUDGET_META[status];
  return <Badge variant={meta.variant}>{meta.label}</Badge>;
}
