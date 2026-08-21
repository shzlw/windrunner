import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import type {ScalarOptions} from '@scalar/docusaurus';

const config: Config = {
  title: 'Windrunner',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: 'https://shzlw.github.io',
  baseUrl: '/',

  organizationName: 'shzlw',
  projectName: 'windrunner',

  onBrokenLinks: 'throw',

  plugins: [
    [
      '@scalar/docusaurus',
      {
        label: 'API Reference',
        route: '/api',
        showNavLink: false,
        configuration: {
          url: '/windrunner-openapi.json',
        },
      } as ScalarOptions,
    ],
  ],

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    colorMode: {
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: 'Windrunner',
      logo: {
        alt: 'Windrunner',
        src: '/img/favicon.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docs',
          position: 'left',
          label: 'Docs',
        },
        {
          to: '/api',
          label: 'API Reference',
          position: 'left',
        },
        {
          to: '/changelog',
          label: 'Changelog',
          position: 'left',
        },
        {
          href: 'https://github.com/shzlw/windrunner',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'light',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Getting started', to: '/docs/getting-started/installation'},
            {label: 'Core concepts', to: '/docs/core-concepts/work-items'},
            {label: 'Guides', to: '/docs/guides/ai-assistance'},
            {label: 'Administration', to: '/docs/administration/users-and-teams'},
          ],
        },
        {
          title: 'Resources',
          items: [
            {label: 'API reference', to: '/api'},
            {label: 'Changelog', to: '/changelog'},
            {label: 'Configuration', to: '/docs/reference/configuration'},
          ],
        },
        {
          title: 'Project',
          items: [
            {label: 'GitHub', href: 'https://github.com/shzlw/windrunner'},
            {label: 'Issues', href: 'https://github.com/shzlw/windrunner/issues'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Windrunner.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
