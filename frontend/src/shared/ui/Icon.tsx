type IconName =
  | 'arrow-left'
  | 'calendar'
  | 'check'
  | 'credit-card'
  | 'edit'
  | 'log-out'
  | 'plus'
  | 'send'
  | 'shield'
  | 'ticket'
  | 'trash'
  | 'user'
  | 'x';

type IconProps = {
  name: IconName;
  size?: number;
};

const paths: Record<IconName, string> = {
  'arrow-left': 'M19 12H5m0 0 6-6M5 12l6 6',
  calendar: 'M7 3v4M17 3v4M4 9h16M5 5h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z',
  check: 'm5 12 4 4L19 6',
  'credit-card': 'M3 7h18v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Zm0 3h18',
  edit: 'M4 20h4L18.5 9.5a2.1 2.1 0 0 0-3-3L5 17v3Zm13-13 3 3',
  'log-out': 'M10 17l5-5-5-5M15 12H3M21 4v16',
  plus: 'M12 5v14M5 12h14',
  send: 'M22 2 11 13M22 2 15 22l-4-9-9-4 20-7Z',
  shield: 'M12 3 5 6v5c0 4 3 7 7 8 4-1 7-4 7-8V9l-7-6Z',
  ticket: 'M4 7h16v4a2 2 0 0 0 0 4v4H4v-4a2 2 0 0 0 0-4V7Z',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3',
  user: 'M20 21a8 8 0 0 0-16 0M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z',
  x: 'M18 6 6 18M6 6l12 12',
};

export function Icon({ name, size = 18 }: IconProps) {
  return (
    <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none">
      <path d={paths[name]} stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
