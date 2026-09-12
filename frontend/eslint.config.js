import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
    },
  },
  {
    files: [
      'src/pages/**/*.{ts,tsx}',
      'src/components/**/*.{ts,tsx}',
      'src/layouts/**/*.{ts,tsx}',
      'src/features/**/*.{ts,tsx}',
    ],
    rules: {
      'no-restricted-globals': ['error', 'fetch'],
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Literal[value=/^https?:\\/\\//]',
          message: '禁止声明绝对 API 地址，只允许站内相对 /portal/api/*',
        },
        {
          selector: 'Literal[value=/^\\/api(\\/|$|\\?|#)/]',
          message: '禁止引用原始管理 /api/* 路径，只允许 /portal/api/*',
        },
      ],
    },
  },
  {
    // 测试 fixture 需要构造恶意地址证明拒绝逻辑，不随制品发布
    files: [
      'src/pages/**/*.test.{ts,tsx}',
      'src/components/**/*.test.{ts,tsx}',
      'src/layouts/**/*.test.{ts,tsx}',
      'src/features/**/*.test.{ts,tsx}',
    ],
    rules: {
      'no-restricted-syntax': 'off',
    },
  },
  {
    ignores: ['dist/', 'coverage/', 'node_modules/'],
  },
);
