import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  docs: [
    {
      type: 'category',
      label: 'Getting Started',
      link: {type: 'doc', id: 'getting-started/installation'},
      items: [
        'getting-started/installation',
        'getting-started/quick-start',
      ],
    },
    {
      type: 'category',
      label: 'Core Concepts',
      link: {type: 'doc', id: 'core-concepts/work-items'},
      items: [
        'core-concepts/work-items',
        'core-concepts/entries',
        'core-concepts/relationships',
        'core-concepts/projects-workspace',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      link: {type: 'doc', id: 'guides/ai-assistance'},
      items: [
        'guides/ai-assistance',
        'guides/search-and-filtering',
        'guides/following-work',
        'guides/account-management',
        'guides/users-and-teams',
        'guides/access-control',
        'guides/audit-log',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      link: {type: 'doc', id: 'reference/api-keys-and-scopes'},
      items: [
        'reference/api-keys-and-scopes',
        'reference/cli',
        'reference/configuration',
        'reference/mcp',
      ],
    },
  ],
};

export default sidebars;
