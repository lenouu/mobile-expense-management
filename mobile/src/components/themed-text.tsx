import { StyleSheet, Text, type TextProps } from 'react-native';

import { Fonts, ThemeColor, Typography } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The only text component the app should use.
 *
 * `type` picks a step from the type scale and `themeColor` picks a token from the palette, so
 * a screen never states a raw fontSize or hex value.
 */
export type ThemedTextType =
  | 'default'
  | 'heading'
  | 'title'
  | 'subtitle'
  | 'body'
  | 'label'
  | 'caption'
  | 'overline'
  | 'small'
  | 'smallBold'
  | 'link'
  | 'linkPrimary'
  | 'code';

export type ThemedTextProps = TextProps & {
  type?: ThemedTextType;
  themeColor?: ThemeColor;
};

export function ThemedText({ style, type = 'default', themeColor, ...rest }: ThemedTextProps) {
  const theme = useTheme();

  return (
    <Text
      style={[
        { color: theme[themeColor ?? 'text'] },
        type === 'default' && styles.body,
        type === 'heading' && styles.heading,
        type === 'title' && styles.title,
        type === 'subtitle' && styles.subtitle,
        type === 'body' && styles.body,
        type === 'label' && styles.label,
        type === 'caption' && styles.caption,
        type === 'overline' && styles.overline,
        type === 'small' && styles.small,
        type === 'smallBold' && styles.smallBold,
        type === 'link' && styles.link,
        type === 'linkPrimary' && styles.linkPrimary,
        type === 'code' && styles.code,
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  heading: Typography.heading,
  title: Typography.title,
  subtitle: Typography.subtitle,
  body: Typography.body,
  label: Typography.label,
  caption: Typography.caption,
  overline: { ...Typography.overline, textTransform: 'uppercase' },
  small: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  smallBold: { fontSize: 14, lineHeight: 20, fontWeight: '700' },
  link: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  linkPrimary: { fontSize: 14, lineHeight: 20, fontWeight: '600', color: '#01916D' },
  code: {
    fontFamily: Fonts.mono,
    fontSize: 12,
    fontWeight: '500',
  },
});
