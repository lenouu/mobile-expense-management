import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
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
  fetchMyProfile,
  updateMyProfile,
  uploadMyPhoto,
} from '@/features/auth/lib/account-api';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
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
 * The administrator's own account (`/api/users/me`).
 *
 * Reached by pressing the avatar in the console header. It deliberately shows only the caller's
 * account: the id comes from the token, so this screen cannot be pointed at another user - the
 * admin directory is where other accounts are managed.
 *
 * Both forms report the backend's answers where the user is looking: a 409 on the username lands
 * under the username field, and a wrong current password lands under the password field.
 */
export function AdminProfileScreen({
  session,
  onAuthError,
  onBack,
}: {
  session: Session;
  onAuthError: (message: string) => void;
  /** Closes the profile and returns to the console tab that was open. */
  onBack?: () => void;
}) {
  const theme = useTheme();
  const { signIn, signOut } = useSession();

  const [profile, setProfile] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [notice, setNotice] = useState<string | null>(null);

  // Username form.
  const [userName, setUserName] = useState(session.userName);
  const [savingName, setSavingName] = useState(false);
  const [nameErrors, setNameErrors] = useState<Record<string, string>>({});

  // Password form.
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordErrors, setPasswordErrors] = useState<Record<string, string>>({});

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

  const saveUserName = async () => {
    const trimmed = userName.trim();
    const errors: Record<string, string> = {};

    if (trimmed.length < USERNAME_MIN || trimmed.length > USERNAME_MAX) {
      errors.userName = `Use between ${USERNAME_MIN} and ${USERNAME_MAX} characters`;
    }
    setNameErrors(errors);
    if (Object.keys(errors).length > 0) return;

    if (trimmed === profile?.userName) {
      setNotice('That is already your username.');
      return;
    }

    setSavingName(true);
    setNotice(null);
    try {
      const updated = await updateMyProfile(session, { userName: trimmed });
      setProfile(updated);
      setUserName(updated.userName);
      // The header initials and greeting read the name from the session, so refresh it too.
      signIn({ ...session, userName: updated.userName });
      setNotice('Username updated.');
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
          form: describeError(error),
        });
        return;
      }
      setNameErrors({ form: describeError(error) });
    } finally {
      setSavingName(false);
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

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      <ConsoleHeader title="My Profile" initials={initialsFrom(session.userName)} />
      <View style={styles.page}>
        <View style={styles.identity}>
          <ProfileAvatar
            initials={initialsFrom(displayName)}
            uri={avatarUri(profile?.profilePictureUrl)}
            busy={uploadingPhoto}
            onPress={pickPhoto}
            onViewPhoto={() => setViewerOpen(true)}
          />
          <ThemedText type="subtitle">{displayName}</ThemedText>
        </View>

        {photoError ? (
          <View style={[styles.banner, { borderColor: Status.danger }]}>
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {photoError}
            </ThemedText>
          </View>
        ) : null}

        <View style={styles.backRow}>
          <Button
            label="Back"
            onPress={() => (onBack ? onBack() : router.replace('/admin'))}
            onCard
          />
          <Button label="Sign out" onPress={handleSignOut} secondary onCard />
        </View>

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
          title="Account"
          subtitle="Read-only. Email and role are not editable from the console."
          action={
            profile ? (
              <Pill label={profile.type} tint={profile.type === 'ADMIN' ? 'brand' : 'neutral'} />
            ) : undefined
          }>
          {loading && !profile ? (
            <ActivityIndicator style={styles.loader} />
          ) : (
            <View style={styles.details}>
              <DetailRow label="Username" value={profile?.userName ?? session.userName} />
              <DetailRow label="Email" value={profile?.email ?? '—'} />
              <DetailRow label="Full name" value={fullName(profile)} />
              <DetailRow label="Status" value={profile?.status ?? '—'} />
              <DetailRow label="Member since" value={memberSince(profile)} />
            </View>
          )}
        </SectionCard>

        <SectionCard
          title="Username"
          subtitle={`Between ${USERNAME_MIN} and ${USERNAME_MAX} characters, unique across the platform.`}>
          <TextField
            label="Username"
            placeholder="e.g. admin.amina"
            autoCapitalize="none"
            autoCorrect={false}
            value={userName}
            onChangeText={setUserName}
            error={nameErrors.userName}
          />

          {nameErrors.form ? (
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {nameErrors.form}
            </ThemedText>
          ) : null}

          <Button
            label={savingName ? 'Saving…' : 'Save username'}
            onPress={saveUserName}
            disabled={savingName}
            onCard
          />
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
}: {
  initials: string;
  /** Absolute URI of the stored picture, or null to show the initials. */
  uri: string | null;
  busy: boolean;
  onPress: () => void;
  /** Opens the picture full screen. Only offered when there is a picture to look at. */
  onViewPhoto: () => void;
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
          { borderColor: theme.adminBackground },
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
