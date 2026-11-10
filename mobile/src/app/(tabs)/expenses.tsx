import { ComingSoonScreen } from '@/screens/shared/coming-soon-screen';

/** The Expenses tab. Its screen is the next one to be built; see the dashboard-first plan. */
export default function ExpensesRoute() {
  return (
    <ComingSoonScreen
      icon="cart"
      title="Expenses"
      message="This is where your spending will live: the list, filters by date and category, and adding or editing an expense."
    />
  );
}
