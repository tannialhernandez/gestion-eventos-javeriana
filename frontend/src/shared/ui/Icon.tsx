type IconName =
  | 'arrow-left'
  | 'calendar'
  | 'check'
  | 'credit-card'
  | 'log-out'
  | 'shield'
  | 'ticket'
  | 'user';

type IconProps = {
  name: IconName;
  size?: number;
};

const paths: Record<IconName, string> = {
  'arrow-left': 'M19 12H5m0 0 6-6M5 12l6 6',
  calendar: 'M7 3v4M17 3v4M4 9h16M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z',
  check: 'm5 12 4 4L19 6',
  'credit-card': 'M3 7h18v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Zm0 3h18',
  'log-out': 'M10 17l5-5-5-5M15 12H3M21 4v16',
  shield: 'M12 3 5 6v5c0 4 3 7 7 8 4-1 7-4 7-8V9l-7-6Z',
  ticket: 'M4 7h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4V7Z',
  user: 'M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z',
};

export function Icon({ name, size = 18 }: IconProps) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d={paths[name]} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
