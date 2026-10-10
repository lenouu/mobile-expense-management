import { AdminConsole } from '@/screens/admin/admin-console';

/**
 * Audit Logs. Reached from the Home tab's Administrative Controls, so it is hidden from the tab
 * bar - the mock-ups give the console four tabs, not five.
 */
export default function AdminLogsRoute() {
  return <AdminConsole tab="logs" />;
}
