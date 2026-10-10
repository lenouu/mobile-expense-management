import { SymbolView } from 'expo-symbols';
import { StyleSheet, View, type ColorValue } from 'react-native';

/**
 * Named icons, so a screen never has to remember a platform-specific symbol name.
 *
 * iOS uses SF Symbols and Android/web use Material Symbols - both are font-based and already
 * installed, which matters because a native icon-font package such as `@expo/vector-icons` cannot
 * be added to an Expo Go project without a rebuild.
 */
export type IconName =
  | 'bell'
  | 'analytics'
  | 'users'
  | 'categories'
  | 'home'
  | 'chat'
  | 'shield'
  | 'sliders'
  | 'document'
  | 'database'
  | 'cloud'
  | 'lock'
  | 'search'
  | 'add'
  | 'more'
  | 'upload'
  | 'download'
  | 'verified'
  | 'clock'
  | 'bolt'
  | 'trendUp'
  | 'trendDown'
  | 'wallet'
  | 'restaurant'
  | 'cart'
  | 'car'
  | 'heart'
  | 'heartPulse';

/**
 * `ios` is an SF Symbol name; `md` is a Material Symbols ligature.
 *
 * Both are checked against the types `expo-symbols` ships - `SFSymbol` and `AndroidSymbol` - so a
 * typo is a compile error instead of a blank space in the tab bar.
 */
export const ICONS = {
  bell: { ios: 'bell.fill', md: 'notifications' },
  analytics: { ios: 'chart.bar.fill', md: 'bar_chart' },
  users: { ios: 'person.2.fill', md: 'group' },
  categories: { ios: 'square.grid.2x2.fill', md: 'grid_view' },
  home: { ios: 'house.fill', md: 'home' },
  chat: { ios: 'bubble.left.and.text.bubble.right.fill', md: 'description' },
  shield: { ios: 'shield.lefthalf.filled', md: 'shield' },
  sliders: { ios: 'slider.horizontal.3', md: 'settings' },
  document: { ios: 'doc.text.fill', md: 'description' },
  database: { ios: 'cylinder.split.1x2.fill', md: 'database' },
  cloud: { ios: 'cloud.fill', md: 'cloud' },
  lock: { ios: 'lock.fill', md: 'lock' },
  search: { ios: 'magnifyingglass', md: 'search' },
  add: { ios: 'plus', md: 'add' },
  more: { ios: 'ellipsis.vertical', md: 'more_vert' },
  upload: { ios: 'square.and.arrow.up', md: 'upload' },
  download: { ios: 'square.and.arrow.down', md: 'download' },
  verified: { ios: 'checkmark.seal.fill', md: 'verified' },
  clock: { ios: 'clock.fill', md: 'schedule' },
  bolt: { ios: 'bolt.fill', md: 'bolt' },
  trendUp: { ios: 'chart.line.uptrend.xyaxis', md: 'trending_up' },
  trendDown: { ios: 'chart.line.downtrend.xyaxis', md: 'trending_down' },
  wallet: { ios: 'wallet.pass.fill', md: 'account_balance_wallet' },
  restaurant: { ios: 'fork.knife', md: 'restaurant' },
  cart: { ios: 'cart.fill', md: 'shopping_cart' },
  car: { ios: 'car.fill', md: 'directions_car' },
  heart: { ios: 'heart.fill', md: 'favorite' },
  heartPulse: { ios: 'heart.text.square.fill', md: 'monitor_heart' },
} as const satisfies Record<IconName, { ios: string; md: string }>;

/** One symbol, sized and tinted. */
export function Icon({
  name,
  size = 20,
  color,
}: {
  name: IconName;
  size?: number;
  /** `ColorValue` rather than `string`: the tab bar hands us an opaque platform colour. */
  color?: ColorValue;
}) {
  const pair = ICONS[name];
  return (
    <SymbolView
      // The pair is structurally what SymbolView wants; the cast only bridges the nominal
      // SFSymbol / AndroidSymbol string unions, which cannot be re-declared here.
      name={{ ios: pair.ios, android: pair.md, web: pair.md } as React.ComponentProps<typeof SymbolView>['name']}
      tintColor={color}
      size={size}
      fallback={<View style={[styles.fallback, { width: size, height: size, backgroundColor: color }]} />}
    />
  );
}

const styles = StyleSheet.create({
  fallback: {
    borderRadius: 4,
    opacity: 0.25,
  },
});
