import { ComingSoonScreen } from '@/screens/shared/coming-soon-screen';

/**
 * The Budget tab.
 *
 * It cannot show anything real yet: the budget module (overall amount, category allocations, the
 * 6% edit window and the edit cap) is not implemented on the backend.
 */
export default function BudgetRoute() {
  return (
    <ComingSoonScreen
      icon="wallet"
      title="Budget"
      message="Set an optional spending goal for a period and split it across categories. Tracking works without it, so this arrives once the budget module exists on the server."
    />
  );
}
