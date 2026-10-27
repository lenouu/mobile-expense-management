import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Button } from '@/components/ui/primitives';
import {
  changeUserStatus,
  changeUserType,
  fetchUserById,
  type AdminSession,
} from '@/features/admin/lib/admin-api';
import { formatDateTime } from '@/features/admin/lib/admin-stats';
import { Spacing, Status } from '@/constants/theme';
import type { Account, AccountState } from '@/types/api';
import { describeError } from '@/utils/errors';

/** The status moves offered for a given state - only the transitions that make sense. */
function statusActions(state: AccountState): { label: string; next: AccountState }[] {
  switch (state) {
    case 'ACTIVE':
      return [
        { label: 'Suspend', next: 'SUSPENDED' },
        { label: 'Delete', next: 'DELETED' },
      ];
    case 'SUSPENDED':
      return [
        { label: 'Reactivate', next: 'ACTIVE' },
        { label: 'Delete', next: 'DELETED' },
      ];
    case 'PENDING':
      return [
        { label: 'Activate', next: 'ACTIVE' },
        { label: 'Delete', next: 'DELETED' },
      ];
    case 'DELETED':
      return [{ label: 'Restore', next: 'ACTIVE' }];
  }
}

/**
 * The status and role controls for one account.
 *
 * Shared by the Users directory and the account detail view so the two can never drift into
 * offering different transitions. The full profile is fetched on mount with
 * `GET /admin/users/{id}`: the list payload is a summary without an email, and the PATCH calls
 * return the profile, so having it here means the wording after a change is accurate.
 */
export function AccountActions({
  session,
  userId,
  onChanged,
  onFailure,
}: {
  session: AdminSession;
  userId: number;
  onChanged: (updated: Account) => void;
  onFailure: (error: unknown) => void;
}) {
  const [detail, setDetail] = useState<Account | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setDetail(null);
    setDetailError(null);
    fetchUserById(session, userId)
      .then((account) => {
        if (!cancelled) setDetail(account);
      })
      .catch((error) => {
        if (cancelled) return;
        setDetailError(describeError(error, { unauthorized: 'Please sign in again.' }));
      });
    return () => {
      cancelled = true;
    };
  }, [session, userId]);

  const run = async (action: () => Promise<Account>) => {
    setBusy(true);
    try {
      const updated = await action();
      setDetail(updated);
      onChanged(updated);
    } catch (error) {
      onFailure(error);
    } finally {
      setBusy(false);
    }
  };

  if (detailError) {
    return (
      <ThemedText type="caption" style={{ color: Status.danger }}>
        {detailError}
      </ThemedText>
    );
  }

  if (!detail) {
    return <ActivityIndicator />;
  }

  const isSelf = detail.userName === session.userName;

  return (
    <View style={styles.container}>
      <View style={styles.line}>
        <ThemedText type="caption" themeColor="textMuted">
          Email
        </ThemedText>
        <ThemedText type="caption" numberOfLines={1}>
          {detail.email}
        </ThemedText>
      </View>
      <View style={styles.line}>
        <ThemedText type="caption" themeColor="textMuted">
          Date of birth
        </ThemedText>
        <ThemedText type="caption">
          {detail.dateOfBirth ? formatDateTime(detail.dateOfBirth) : 'Not provided'}
        </ThemedText>
      </View>
      <View style={styles.line}>
        <ThemedText type="caption" themeColor="textMuted">
          Registered
        </ThemedText>
        <ThemedText type="caption">{formatDateTime(detail.createdAt)}</ThemedText>
      </View>

      {isSelf ? (
        <ThemedText type="caption" style={{ color: Status.warning }}>
          This is your own account. Suspending it, or removing the ADMIN role, ends your access to
          this console.
        </ThemedText>
      ) : null}

      <ThemedText type="label">Account status</ThemedText>
      <View style={styles.actions}>
        {statusActions(detail.status).map((action) => (
          <Button
            key={action.next}
            label={action.label}
            disabled={busy}
            onCard
            onPress={() =>
              void run(() =>
                changeUserStatus(session, detail.id, {
                  status: action.next,
                  reason: `Set to ${action.next} from the admin console (was ${detail.status})`,
                }),
              )
            }
          />
        ))}
      </View>

      <ThemedText type="label">Role</ThemedText>
      <View style={styles.actions}>
        {detail.type === 'USER' ? (
          <Button
            label="Promote to admin"
            disabled={busy}
            onCard
            onPress={() =>
              void run(() =>
                changeUserType(session, detail.id, {
                  type: 'ADMIN',
                  reason: 'Promoted from the admin console',
                }),
              )
            }
          />
        ) : (
          <Button
            label="Demote to user"
            disabled={busy}
            onCard
            onPress={() =>
              void run(() =>
                changeUserType(session, detail.id, {
                  type: 'USER',
                  reason: 'Demoted from the admin console',
                }),
              )
            }
          />
        )}
      </View>

      <ThemedText type="caption" themeColor="textMuted">
        The backend refuses to demote the last remaining admin and answers 409 with an explanation.
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.two,
    paddingTop: Spacing.two,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(148, 163, 184, 0.4)',
  },
  line: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  actions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
});
