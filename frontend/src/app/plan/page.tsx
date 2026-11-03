import type { Metadata } from "next";

import { GuestPlanView } from "@/components/trip/guest-plan-view";

export const metadata: Metadata = { title: "Plan a trip" };

export default function PlanPage() {
  return <GuestPlanView />;
}
