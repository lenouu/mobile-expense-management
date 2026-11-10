import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Button } from '@/components/ui/primitives';
import { TextField } from '@/components/ui/text-field';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import { Pill, SectionCard } from '@/features/admin/components/console-ui';
import {
  avatarUri,
  changeMyPassword,
  deleteMyAccount,
  fetchMyProfile,
  updateMyProfile,
  uploadMyPhoto,
} from '@/features/auth/lib/account-api';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import { ProfileHeader } from '@/screens/profile/profile-header';
import type { Account, Session } from '@/types/api';
import { describeError } from '@/utils/errors';

/** Matches the backend's `@Size` on the username, so the answer arrives before the request. */
const USERNAME_MIN = 3;
const USERNAME_MAX = 30;

/** Matches the backend's own minimum, so an obviously short password is caught locally. */
const PASSWORD_MIN = 8;

/** A notice stays up long enough to read, then clears itself. */
const NOTICE_MS = 4000;

/**
 * The signed-in account's own profile (`/api/users/me`), for either role.
 *
 * It shows only the caller's account: the id comes from the token, so this screen cannot be
 * pointed at anybody else - managing other accounts happens in the admin directory.
 *
 * The same screen serves both roles. `consoleChrome` is the only difference: inside the admin
 * console it keeps the console header and its darker page background; reached from the normal app
 * it just draws on the app background, because an ordinary user has no console header to show.
 *
 * Both forms report the backend's answers where the user is looking: a 409 on the username lands
 * under the username field, and a wrong current password lands under the password field.
 */
export function ProfileScreen({
  session,
  onAuthError,
  onBack,
  consoleChrome = true,
}: {
  session: Session;
  onAuthError: (message: string) => void;
  /** Closes the profile and returns to wherever it was opened from. */
  onBack?: () => void;
  /** True inside the admin console: draws its header and darker page background. */
  consoleChrome?: boolean;
}) {
  const theme = useTheme();
  const { signIn, signOut } = useSession();

  // The console sits on its own pale blue; the rest of the app uses the normal page background.
  const pageBackground = consoleChrome ? theme.adminBackground : theme.background;

  const [profile, setProfile] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [notice, setNotice] = useState<string | null>(null);

  // Details form: username and the two name fields save together, so the screen has one place to
  // change who you are rather than a form per field.
  const [userName, setUserName] = useState(session.userName);
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [savingDetails, setSavingDetails] = useState(false);
  const [nameErrors, setNameErrors] = useState<Record<string, string>>({});

  // Password form.
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>({});

  // Account deletion, which stays behind an explicit confirm step and a password.
  const [deleting, setDeleting] = useState(false);
  const [deletePassword, setDeletePassword] = useState('');
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  // Profile picture.
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [photoError, setPhotoError] = useState<string | null>(null);
  /** The full-screen viewer, opened by pressing the picture itself. */
  const [viewerOpen, setViewerOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const fetched = await fetchMyProfile(session);
      setProfile(fetched);
      setUserName(fetched.userName);
      setFirstName(fetched.firstName ?? '');
      setLastName(fetched.lastName ?? '');
    } catch (error) {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
        onAuthError(
          error.status === 401
            ? 'Your session has expired. Please sign in again.'
            : 'This account no longer has administrator access.',
        );
        return;
      }
      setLoadError(describeError(error, { unauthorized: 'Please sign in again.' }));
    } finally {
      setLoading(false);
    }
  }, [session, onAuthError]);

  useEffect(() => {
    void load();
  }, [load]);

  // Clear a notice on its own, so a stale "Saved" never sits under a fresh edit.
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), NOTICE_MS);
    return () => clearTimeout(timer);
  }, [notice]);

  const saveDetails = async () => {
    const trimmedUserName = userName.trim();
    const trimmedFirst = firstName.trim();
    const trimmedLast = lastName.trim();
    const errors: Record<string, string> = {};

    if (trimmedUserName.length < USERNAME_MIN || trimmedUserName.length > USERNAME_MAX) {
      errors.userName = `Use between ${USERNAME_MIN} and ${USERNAME_MAX} characters`;
    }
    setNameErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const unchanged =
      trimmedUserName === (profile?.userName ?? '') &&
      trimmedFirst === (profile?.firstName ?? '') &&
      trimmedLast === (profile?.lastName ?? '');
    if (unchanged) {
      setNotice('Nothing has changed.');
      return;
    }

    setSavingDetails(true);
    setNotice(null);
    try {
      const updated = await updateMyProfile(session, {
        userName: trimmedUserName,
        firstName: trimmedFirst,
        lastName: trimmedLast,
      });
      setProfile(updated);
      setUserName(updated.userName);
      setFirstName(updated.firstName ?? '');
      setLastName(updated.lastName ?? '');
      // The header initials and greeting read the name from the session, so refresh it too.
      signIn({ ...session, userName: updated.userName });
      setNotice('Profile updated.');
    } catch (error) {
      if (error instanceof ApiRequestError) {
        if (error.status === 401) {
          onAuthError('Your session has expired. Please sign in again.');
          return;
        }
        if (error.status === 409) {
          setNameErrors({ userName: 'That username is already taken.' });
          return;
        }
        setNameErrors({
          ...(error.fields.userName ? { userName: error.fields.userName } : {}),
          ...(error.fields.firstName ? { firstName: error.fields.firstName } : {}),
          ...(error.fields.lastName ? { lastName: error.fields.lastName } : {}),
          form: describeError(error),
        });
        return;
      }
      setNameErrors({ form: describeError(error) });
    } finally {
      setSavingDetails(false);
    }
  };

  const savePassword = async () => {
    const errors: Record<string, string> = {};

    if (!oldPassword) errors.oldPassword = 'Enter your current password';
    if (newPassword.length < PASSWORD_MIN) {
      errors.newPassword = `Use at least ${PASSWORD_MIN} characters`;
    }
    if (newPassword !== confirmPassword) {
      errors.confirmNewPassword = 'The two passwords do not match';
    }
    setPasswordErrors(errors);
    if (Object.keys(errors).length > 0) return;

    setSavingPassword(true);
    setNotice(null);
    try {
      await changeMyPassword(session, {
        oldPassword,
        newPassword,
        confirmNewPassword: confirmPassword,
      });
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setNotice('Password changed.');
    } catch (error) {
      if (error instanceof ApiRequestError) {
        if (error.status === 401) {
          // 401 here means the current password was wrong, not that the token expired: the
          // backend checks the supplied password against the stored hash.
          setPasswordErrors({ oldPassword: 'That is not your current password.' });
          return;
        }
        setPasswordErrors({
          ...(error.fields.newPassword ? { newPassword: error.fields.newPassword } : {}),
          ...(error.fields.confirmNewPassword
            ? { confirmNewPassword: error.fields.confirmNewPassword }
            : {}),
          form: describeError(error),
        });
        return;
      }
      setPasswordErrors({ form: describeError(error) });
    } finally {
      setSavingPassword(false);
    }
  };

  // The saved name, so the circle and the heading follow a rename immediately.
  const displayName = profile?.userName ?? session.userName;

  /**
   * Opens the photo library, then uploads what was picked.
   *
   * Every await lives inside the try, including the permission check and the picker itself. That
   * matters: a throw before the try would be an unhandled rejection - the press would appear to do
   * nothing at all, with no request and no message, which is exactly how this failed in practice.
   *
   * The busy flag is set before anything is awaited, so the camera badge turns into a spinner the
   * moment the badge is pressed and keeps spinning while the gallery or crop editor is in front.
   *
   * The server stores the picture under a new random name and returns the updated profile, so the
   * `profilePictureUrl` in `updated` is already a different URL from the old one - which is what
   * makes every client re-download it instead of showing a cached copy.
   */
  const pickPhoto = async () => {
    if (uploadingPhoto) return;

    setPhotoError(null);
    setNotice(null);
    setUploadingPhoto(true);

    try {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        setPhotoError('Allow photo library access to choose a profile picture.');
        return;
      }

      const picked = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsEditing: true,
        aspect: [1, 1],
        quality: 0.8,
        // The upload sends the picture base64-encoded in a JSON body, so ask the picker for the
        // bytes directly instead of reading the file ourselves.
        base64: true,
      });

      // `assets` can be null rather than merely empty, so both are checked before reading it.
      if (picked.canceled || !picked.assets || picked.assets.length === 0) return;

      const base64 = picked.assets[0].base64;
      if (!base64) {
        setPhotoError('That image could not be read on this device. Try another one.');
        return;
      }

      // Roughly the server's 5 MB limit once decoded, checked here so a huge photo fails fast
      // instead of being uploaded just to be rejected.
      if (base64.length > 7 * 1024 * 1024) {
        setPhotoError('That picture is too large. Choose a smaller one.');
        return;
      }

      const updated = await uploadMyPhoto(session, base64);
      setProfile(updated);
      setNotice('Profile picture updated.');
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        onAuthError('Your session has expired. Please sign in again.');
        return;
      }
      setPhotoError(describeError(error));
    } finally {
      setUploadingPhoto(false);
    }
  };

  /**
   * Ends the session and returns to the login screen.
   *
   * The session lives only in memory, so this is the whole sign-out: nothing is stored that would
   * have to be erased, and there is no server-side revocation to call (the token simply expires).
   */
  const handleSignOut = () => {
    signOut();
    router.replace('/auth/sign-in');
  };

  /**
   * Soft-deletes the account after asking for the password again.
   *
   * The backend keeps the row and the history and refuses the credential of a deleted account, so
   * this is not recoverable from inside the app - which is exactly why it sits behind a confirm
   * step and a password rather than a single button.
   */
  const deleteAccount = async () => {
    if (!deletePassword) {
      setDeleteError('Enter your password to confirm');
      return;
    }

    setDeleteBusy(true);
    setDeleteError(null);
    try {
      await deleteMyAccount(session, { password: deletePassword });
      signOut();
      router.replace('/auth/sign-in');
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) {
        setDeleteError('That is not your password.');
        return;
      }
      setDeleteError(describeError(error));
    } finally {
      setDeleteBusy(false);
    }
  };

  const cancelDelete = () => {
    setDeleting(false);
    setDeletePassword('');
    setDeleteError(null);
  };

  return (
    <ScrollView
      style={{ backgroundColor: pageBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      {consoleChrome ? (
        <ConsoleHeader title="My Profile" initials={initialsFrom(session.userName)} />
      ) : (
        <ProfileHeader />
      )}
      <View style={styles.page}>
        <View style={styles.identity}>
          <ProfileAvatar
            initials={initialsFrom(displayName)}
            uri={avatarUri(profile?.profilePictureUrl)}
            busy={uploadingPhoto}
            onPress={pickPhoto}
            onViewPhoto={() => setViewerOpen(true)}
            chromeColor={pageBackground}
          />
          <ThemedText type="subtitle">{displayName}</ThemedText>
          {fullName(profile) !== '—' ? (
            <ThemedText type="small" themeColor="textSecondary">
              {fullName(profile)}
            </ThemedText>
          ) : null}
        </View>

        {photoError ? (
          <View style={[styles.banner, { borderColor: Status.danger }]}>
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {photoError}
            </ThemedText>
          </View>
        ) : null}

        {consoleChrome ? (
          <View style={styles.backRow}>
            <Button
              label="Back"
              onPress={() => (onBack ? onBack() : router.replace('/admin'))}
              onCard
            />
          </View>
        ) : null}

        {notice ? (
          <View style={[styles.banner, { borderColor: Status.success }]}>
            <ThemedText type="caption" style={{ color: Status.success }}>
              {notice}
            </ThemedText>
          </View>
        ) : null}

        {loadError ? (
          <View style={[styles.banner, { borderColor: Status.danger }]}>
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {loadError}
            </ThemedText>
          </View>
        ) : null}

        <SectionCard
          title="Your details"
          subtitle="Your name and username are yours to change. The email is fixed once the account exists."
          action={
            profile ? (
              <Pill label={profile.type} tint={profile.type === 'ADMIN' ? 'brand' : 'neutral'} />
            ) : undefined
          }>
          {loading && !profile ? (
            <ActivityIndicator style={styles.loader} />
          ) : (
            <>
              <TextField
                label="Username"
                placeholder="e.g. sara.mansouri"
                autoCapitalize="none"
                autoCorrect={false}
                value={userName}
                onChangeText={setUserName}
                error={nameErrors.userName}
              />

              <TextField
                label="First name"
                placeholder="Your first name"
                autoCapitalize="words"
                autoCorrect={false}
                value={firstName}
                onChangeText={setFirstName}
                error={nameErrors.firstName}
              />

              <TextField
                label="Last name"
                placeholder="Your last name"
                autoCapitalize="words"
                autoCorrect={false}
                value={lastName}
                onChangeText={setLastName}
                error={nameErrors.lastName}
              />

              {nameErrors.form ? (
                <ThemedText type="caption" style={{ color: Status.danger }}>
                  {nameErrors.form}
                </ThemedText>
              ) : null}

              <Button
                label={savingDetails ? 'Saving…' : 'Save changes'}
                onPress={saveDetails}
                disabled={savingDetails}
                onCard
              />

              <View style={styles.details}>
                <DetailRow label="Email" value={profile?.email ?? '—'} />
                <DetailRow label="Status" value={profile?.status ?? '—'} />
                <DetailRow label="Member since" value={memberSince(profile)} />
              </View>
            </>
          )}
        </SectionCard>

        <SectionCard
          title="Password"
          subtitle="At least 8 characters. Changing it does not sign out other devices."
        >
          <TextField
            label="Current password"
            placeholder="Your password now"
            autoCapitalize="none"
            autoCorrect={false}
            revealable
            secureTextEntry
            value={oldPassword}
            onChangeText={setOldPassword}
            error={passwordErrors.oldPassword}
          />

          <TextField
            label="New password"
            placeholder="At least 8 characters"
            autoCapitalize="none"
            autoCorrect={false}
            revealable
            secureTextEntry
            value={newPassword}
            onChangeText={setNewPassword}
            error={passwordErrors.newPassword}
          />

          <TextField
            label="Confirm new password"
            placeholder="Repeat the new password"
            autoCapitalize="none"
            autoCorrect={false}
            revealable
            secureTextEntry
            value={confirmPassword}
            onChangeText={setConfirmPassword}
            error={passwordErrors.confirmNewPassword}
          />

          {passwordErrors.form ? (
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {passwordErrors.form}
            </ThemedText>
          ) : null}

          <Button
            label={savingPassword ? 'Changing…' : 'Change password'}
            onPress={savePassword}
            disabled={savingPassword}
            onCard
          />
        </SectionCard>

        <SectionCard
          title="Session"
          subtitle="The only place you are signed in right now."
        >
          <View style={styles.details}>
            <DetailRow label="Signed in as" value={session.userName} />
            <DetailRow
              label="Account type"
              value={session.type === 'ADMIN' ? 'Administrator' : 'User'}
            />
          </View>

          <ThemedText type="caption" themeColor="textMuted">
            The session is held in memory only: closing the app signs you out, and nothing about
            your account is left behind on the device.
          </ThemedText>

          <Button label="Sign out" onPress={handleSignOut} secondary onCard />
        </SectionCard>

        <SectionCard title="Privacy" subtitle="Your finances are yours alone.">
          <ThemedText type="small" themeColor="textSecondary">
            Only you can see your income, expenses and budgets. Administrators manage accounts;
            they never read your financial records.
          </ThemedText>

          {session.type === 'USER' ? (
            deleting ? (
              <>
                <TextField
                  label="Your password"
                  placeholder="Confirm with your password"
                  autoCapitalize="none"
                  autoCorrect={false}
                  revealable
                  secureTextEntry
                  value={deletePassword}
                  onChangeText={setDeletePassword}
                  error={deleteError ?? undefined}
                />

                <ThemedText type="caption" themeColor="textMuted">
                  This deletes the account for good: you will not be able to sign in again, and
                  your username and email stay reserved. Your recorded history is kept.
                </ThemedText>

                <View style={styles.deleteRow}>
                  <DangerButton
                    label={deleteBusy ? 'Deleting…' : 'Delete my account'}
                    onPress={deleteAccount}
                    disabled={deleteBusy}
                  />
                  <Button
                    label="Cancel"
                    onPress={cancelDelete}
                    disabled={deleteBusy}
                    secondary
                    onCard
                  />
                </View>
              </>
            ) : (
              <>
                <ThemedText type="caption" themeColor="textMuted">
                  Deleting stops you signing in for good. You will be asked for your password.
                </ThemedText>

                <Button
                  label="Delete my account"
                  onPress={() => setDeleting(true)}
                  secondary
                  onCard
                />
              </>
            )
          ) : (
            <ThemedText type="caption" themeColor="textMuted">
              An administrator account is deleted from the admin directory rather than from here,
              so the console can never lose its last administrator by accident.
            </ThemedText>
          )}
        </SectionCard>

        <SectionCard title="About" subtitle="FinFlow">
          <View style={styles.details}>
            <DetailRow label="Version" value={Constants.expoConfig?.version ?? '—'} />
          </View>
          <ThemedText type="caption" themeColor="textMuted">
            Track. Understand. Improve.
          </ThemedText>
        </SectionCard>
      </View>

      {/* A Modal draws in its own window, so keeping it in the scroll content costs nothing. */}
      <ProfilePhotoViewer
        uri={avatarUri(profile?.profilePictureUrl)}
        visible={viewerOpen}
        onClose={() => setViewerOpen(false)}
      />
    </ScrollView>
  );
}

/**
 * The signed-in account's picture, or its initials, with a camera badge to change it.
 *
 * Two separate targets, deliberately: pressing the picture opens it full screen (the way a
 * messenger does), pressing the camera badge changes it. The badge is a sibling of the circle
 * rather than a child, so it can sit half-over the edge without being clipped, and while an upload
 * is running it becomes a spinner so the press always has feedback.
 */
function ProfileAvatar({
  initials,
  uri,
  busy,
  onPress,
  onViewPhoto,
  chromeColor,
}: {
  initials: string;
  /** Absolute URI of the stored picture, or null to show the initials. */
  uri: string | null;
  busy: boolean;
  onPress: () => void;
  /** Opens the picture full screen. Only offered when there is a picture to look at. */
  onViewPhoto: () => void;
  /** The page background, so the badge's border cuts a gap into the circle on either chrome. */
  chromeColor: string;
}) {
  const theme = useTheme();

  return (
    <View style={styles.avatarWrap}>
      <View style={[styles.bigAvatar, { borderColor: theme.border }]}>
        {uri ? (
          <Pressable
            onPress={onViewPhoto}
            accessibilityRole="imagebutton"
            accessibilityLabel="View profile picture"
            style={({ pressed }) => [styles.bigAvatarImagePress, pressed && styles.pressed]}>
            <Image
              source={{ uri }}
              style={styles.bigAvatarImage}
              contentFit="cover"
              transition={150}
              accessibilityLabel="Profile picture"
            />
          </Pressable>
        ) : (
          <ThemedText style={styles.bigAvatarText}>{initials}</ThemedText>
        )}
      </View>

      <Pressable
        onPress={onPress}
        disabled={busy}
        accessibilityRole="button"
        accessibilityLabel="Change profile picture"
        hitSlop={8}
        style={({ pressed }) => [
          styles.cameraBadge,
          { borderColor: chromeColor },
          (pressed || busy) && styles.pressed,
        ]}>
        {busy ? (
          <ActivityIndicator size="small" color="#FFFFFF" />
        ) : (
          <Icon name="camera" size={18} color="#FFFFFF" />
        )}
      </Pressable>
    </View>
  );
}

/**
 * The profile picture, full screen on a dark backdrop.
 *
 * A viewer, not a gallery: one image, no zoom or swipe, so a single tap - or the hardware back
 * button, through `onRequestClose` - always dismisses it. That is how a messenger shows an avatar,
 * and it keeps the modal impossible to get stuck in.
 */
function ProfilePhotoViewer({
  uri,
  visible,
  onClose,
}: {
  uri: string | null;
  visible: boolean;
  onClose: () => void;
}) {
  const insets = useSafeAreaInsets();

  if (!uri) return null;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      onRequestClose={onClose}>
      <Pressable
        style={styles.viewerBackdrop}
        onPress={onClose}
        accessibilityRole="button"
        accessibilityLabel="Close picture">
        <Image
          source={{ uri }}
          style={styles.viewerImage}
          contentFit="contain"
          accessibilityLabel="Profile picture, full screen"
        />

        <Pressable
          onPress={onClose}
          hitSlop={12}
          accessibilityRole="button"
          accessibilityLabel="Close"
          style={[styles.viewerClose, { top: insets.top + Spacing.three }]}>
          <ThemedText style={styles.viewerCloseGlyph}>✕</ThemedText>
        </Pressable>
      </Pressable>
    </Modal>
  );
}

/** One read-only fact about the account. */
function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.detailRow}>
      <ThemedText type="caption" themeColor="textMuted">
        {label}
      </ThemedText>
      <ThemedText type="small">{value}</ThemedText>
    </View>
  );
}

function fullName(profile: Account | null): string {
  if (!profile) return '—';
  const name = `${profile.firstName ?? ''} ${profile.lastName ?? ''}`.trim();
  return name || '—';
}

/** `createdAt` is a local date-time without an offset, which JS reads as local time. */
function memberSince(profile: Account | null): string {
  if (!profile) return '—';
  const date = new Date(profile.createdAt);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString();
}

/**
 * The destructive action on this screen.
 *
 * It is the one control that is not the shared `Button`, because the design system has no danger
 * variant and deleting an account should not look like saving one.
 */
function DangerButton({
  label,
  onPress,
  disabled,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      style={({ pressed }) => [styles.dangerButton, (pressed || disabled) && styles.pressed]}>
      <ThemedText type="smallBold" style={{ color: Status.danger }}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingBottom: Spacing.six,
  },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  identity: {
    alignItems: 'center',
    gap: Spacing.two,
    paddingTop: Spacing.two,
  },
  /** Holds the circle and the badge, so the badge can overlap the circle's edge. */
  avatarWrap: {
    position: 'relative',
  },
  bigAvatar: {
    width: 112,
    height: 112,
    borderRadius: 56,
    borderWidth: 3,
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
  },
  bigAvatarImage: {
    width: '100%',
    height: '100%',
  },
  /** The circle's whole face is the tap target for the full-screen view. */
  bigAvatarImagePress: {
    width: '100%',
    height: '100%',
  },
  viewerBackdrop: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.94)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  viewerImage: {
    width: '100%',
    height: '100%',
  },
  viewerClose: {
    position: 'absolute',
    right: Spacing.four,
    width: 40,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 255, 255, 0.16)',
  },
  viewerCloseGlyph: {
    color: '#FFFFFF',
    fontSize: 18,
    lineHeight: 22,
    fontWeight: '700',
  },
  bigAvatarText: {
    color: '#FFFFFF',
    fontSize: 36,
    lineHeight: 44,
    fontWeight: '700',
    letterSpacing: 1,
  },
  /** The camera affordance, sitting on the circle's lower-right edge. */
  cameraBadge: {
    position: 'absolute',
    right: -2,
    bottom: -2,
    width: 36,
    height: 36,
    borderRadius: 18,
    borderWidth: 3,
    backgroundColor: Brand.green,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
  backRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: Spacing.two,
  },
  deleteRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  dangerButton: {
    borderRadius: Radius.field,
    borderWidth: 1,
    borderColor: Status.danger,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two + Spacing.half,
  },
  details: {
    gap: Spacing.two,
  },
  detailRow: {
    gap: Spacing.half,
  },
  banner: {
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.two + Spacing.half,
  },
  loader: {
    paddingVertical: Spacing.five,
  },
});
