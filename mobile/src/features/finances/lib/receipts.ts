import * as ImagePicker from 'expo-image-picker';

/**
 * Receipt photographs, for the length of one app session.
 *
 * <p>Deliberately <strong>not</strong> written to disk. A receipt is evidence for a record that
 * lives on the server, so the permanent copy belongs there too; keeping one on the phone would
 * survive a restart but die on uninstall, and would leave the proof and the expense on different
 * machines. Until the upload endpoint exists, a captured photo lives here in memory: it can be
 * seen and removed while the app is running, and it is gone when the app is closed. That is the
 * honest behaviour for a preview, and it is stated in the form rather than left to be discovered.
 *
 * <p>A receipt carries its own id and is linked to expenses, rather than being a property of one
 * expense. One receipt covering several expenses is a case the product wants to support (a single
 * till slip split across categories), so the shape here is many-to-many from the start: the link
 * is a list even while the form only ever creates one.
 */
export type Receipt = {
  /** Its own identifier, not the expense's: one receipt may cover several expenses later. */
  id: string;
  /** The picker's local file, for showing the photo. Not durable - see the note above. */
  uri: string;
  /** The bytes, so a future upload does not have to re-read the file. Absent if unreadable. */
  base64: string | null;
  width: number;
  height: number;
  capturedAt: string;
  source: 'camera' | 'library';
};

/** What the form gets back: a receipt, a message to show, or nothing (the user backed out). */
export type CaptureResult =
  | { receipt: Receipt }
  | { error: string }
  | { cancelled: true };

let sequence = 0;

function nextId(): string {
  sequence += 1;
  return `receipt_${Date.now().toString(36)}_${sequence.toString(36)}`;
}

/**
 * Opens the camera or the photo library and returns a compressed photo.
 *
 * Compression is set here rather than left to the caller: a receipt only has to be legible, and a
 * 12 MP phone photo is roughly fifty times the size for no extra information.
 */
export async function captureReceipt(source: 'camera' | 'library'): Promise<CaptureResult> {
  try {
    if (source === 'camera') {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        return { error: 'Camera access is off. Turn it on in Settings, or choose a photo instead.' };
      }
    } else {
      const permission = await ImagePicker.requestMediaLibraryPermissionsAsync();
      if (!permission.granted) {
        return { error: 'Photo library access is off. Turn it on in Settings to attach a receipt.' };
      }
    }

    const options: ImagePicker.ImagePickerOptions = {
      mediaTypes: ['images'],
      // No cropping: a receipt is tall, and the whole slip is what matters.
      allowsEditing: false,
      exif: false,
      quality: 0.6,
      base64: true,
    };

    const picked =
      source === 'camera'
        ? await ImagePicker.launchCameraAsync(options)
        : await ImagePicker.launchImageLibraryAsync(options);

    // `assets` can be null rather than merely empty, so both are checked.
    if (picked.canceled || !picked.assets || picked.assets.length === 0) {
      return { cancelled: true };
    }

    const asset = picked.assets[0];

    return {
      receipt: {
        id: nextId(),
        uri: asset.uri,
        base64: asset.base64 ?? null,
        width: asset.width,
        height: asset.height,
        capturedAt: new Date().toISOString(),
        source,
      },
    };
  } catch (e) {
    // A simulator or a device without a camera rejects the call rather than returning nothing.
    const message = e instanceof Error ? e.message : 'Could not open the camera.';
    return {
      error:
        source === 'camera'
          ? `No camera available (${message}). Choose a photo from the library instead.`
          : `Could not open the photo library (${message}).`,
    };
  }
}

/**
 * The receipts attached to each expense, for now only in memory.
 *
 * A `Map` of lists rather than a single slot: the storage shape is many-to-many, even though the
 * form currently offers one photo per expense.
 */
const attached = new Map<string, Receipt[]>();

export function linkReceipt(expenseId: string, receipt: Receipt): void {
  const existing = attached.get(expenseId) ?? [];
  // Replacing rather than appending: one photo per expense until the scan flow lands.
  attached.set(expenseId, [receipt, ...existing.filter((one) => one.id !== receipt.id)]);
}

export function receiptsFor(expenseId: string): Receipt[] {
  return attached.get(expenseId) ?? [];
}

export function unlinkReceipts(expenseId: string): void {
  attached.delete(expenseId);
}

/** Called on sign-out so a receipt never outlives the session it was captured in. */
export function forgetAllReceipts(): void {
  attached.clear();
}
