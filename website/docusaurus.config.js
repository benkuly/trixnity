// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const {themes} = require('prism-react-renderer');
const lightTheme = themes.github;
const darkTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Trixnity',
  tagline: 'One Matrix SDK for (almost) everything',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://trixnity.gitlab.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/trixnity',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'Trixnity', // Usually your GitHub org/user name.
  projectName: 'Trixnity', // Usually your repo name.

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl: 'https://gitlab.com/trixnity/trixnity/-/tree/main/website',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Replace with your project's social card
      image: 'img/docusaurus-social-card.jpg',
      navbar: {
        title: 'Trixnity',
        logo: {
          alt: 'My Site Logo',
          src: 'img/logo.png',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'documentationSidebar',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: 'pathname:///api',
            label: 'API',
            position: 'left',
          },
          {
            href: 'https://gitlab.com/trixnity/trixnity',
            label: 'Repository',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Documentation',
                to: '/docs',
              },
              {
                label: 'API',
                href: 'pathname:///api',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'Matrix',
                href: 'matrix:r/trixnity:imbitbu.de',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Repository',
                href: 'https://gitlab.com/trixnity/trixnity',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Trixnity`,
      },
      prism: {
        theme: lightTheme,
        darkTheme: darkTheme,
        additionalLanguages: ['kotlin'],
      },
    }),
};

module.exports = config;
