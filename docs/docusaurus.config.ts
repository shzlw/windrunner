import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';
import type {ScalarOptions} from '@scalar/docusaurus';

const config: Config = {
  title: 'Windrunner',
  tagline: 'Ask the project. Move work forward.',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  url: 'https://shzlw.github.io',
  baseUrl: '/windrunner/',

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
          url: '/windrunner/windrunner-openapi.json',
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
      style: 'dark',
      links: [
        {
          title: 'Product',
          items: [
            {label: 'AI assistance', to: '/docs/guides/ai-assistance'},
            {label: 'Projects & workspace', to: '/docs/core-concepts/projects-workspace'},
            {label: 'Work items', to: '/docs/core-concepts/work-items'},
            {label: 'Following work', to: '/docs/guides/following-work'},
            {label: 'Audit log', to: '/docs/guides/audit-log'},
          ],
        },
        {
          title: 'Learn',
          items: [
            {label: 'Getting started', to: '/docs/getting-started/installation'},
            {label: 'Core concepts', to: '/docs/core-concepts/work-items'},
            {label: 'Search & filtering', to: '/docs/guides/search-and-filtering'},
            {label: 'Users & teams', to: '/docs/guides/users-and-teams'},
          ],
        },
        {
          title: 'Developers',
          items: [
            {label: 'API reference', to: '/api'},
            {label: 'MCP server', to: '/docs/reference/mcp'},
            {label: 'CLI', to: '/docs/reference/cli'},
            {label: 'Configuration', to: '/docs/reference/configuration'},
          ],
        },
        {
          title: 'Open source',
          items: [
            {label: 'Changelog', to: '/changelog'},
            {label: 'GitHub', href: 'https://github.com/shzlw/windrunner'},
            {label: 'Issues', href: 'https://github.com/shzlw/windrunner/issues'},
          ],
        },
      ],
      logo: {
        alt: 'Windrunner',
        src: 'img/favicon.svg',
        href: '/',
        width: 32,
        height: 32,
      },
      copyright: `Copyright © ${new Date().getFullYear()} Windrunner · AI-powered work management for teams.`,
    },
    prism: {
      theme: prismThemes.oneLight,
      darkTheme: prismThemes.oneDark,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
