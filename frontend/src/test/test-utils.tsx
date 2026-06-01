import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom';
import { AuthProvider } from '../features/auth/AuthContext';

type ProviderOptions = Omit<RenderOptions, 'wrapper'> & {
  routerProps?: MemoryRouterProps;
};

function AllProviders({ children, routerProps }: { children: ReactNode; routerProps?: MemoryRouterProps }) {
  return (
    <MemoryRouter {...routerProps}>
      <AuthProvider>{children}</AuthProvider>
    </MemoryRouter>
  );
}

export function renderWithProviders(ui: ReactElement, options: ProviderOptions = {}) {
  const { routerProps, ...renderOptions } = options;
  return render(ui, {
    wrapper: ({ children }) => <AllProviders routerProps={routerProps}>{children}</AllProviders>,
    ...renderOptions,
  });
}

export * from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';
