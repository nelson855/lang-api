export const zhCN = {
  common: {
    retry: '重试',
    backHome: '返回首页',
    language: '语言',
    close: '关闭',
    prevPage: '上一页',
    nextPage: '下一页',
    pageStatus: '第 {{current}} / {{total}} 页',
    required: '必填',
  },
  nav: {
    home: '首页',
    models: '模型广场',
    docs: '开发文档',
    login: '登录',
    register: '注册',
    dashboard: '控制台',
    menu: '菜单',
    closeMenu: '关闭菜单',
  },
  pages: {
    home: {
      title: '自有语言模型网关',
      subtitle: '欢迎来到 {{siteName}}',
      statusNote: '模型与认证能力按阶段接入，当前只提供基础入口。',
    },
    models: {
      title: '模型广场',
      empty: '模型数据尚未接入，交付后此处展示真实模型。',
    },
    docs: {
      title: '开发文档',
      empty: '文档正文将在后续阶段接入，此处只保留入口框架。',
    },
    login: {
      title: '登录',
      unavailable: '认证功能尚未开放，接入后可登录。',
    },
    register: {
      title: '注册',
      unavailable: '注册策略待确认，暂不开放。',
    },
    dashboard: {
      title: '控制台',
      empty: '控制台数据将在后续阶段接入。',
    },
    notFound: {
      title: '页面不存在',
      description: '该地址没有对应页面，请返回首页。',
    },
  },
  states: {
    loading: '加载中…',
    empty: '暂无数据',
    startup: {
      loading: '正在启动，读取运行时配置…',
      error: '运行时配置加载失败，请检查后端服务后重试。',
    },
    error: {
      title: '请求失败',
    },
    forbidden: {
      title: '无权限',
      description: '当前身份无法访问该页面。',
    },
  },
  errors: {
    network: '网络请求失败，请重试。',
    invalidResponse: '服务端响应无法解析。',
    requestId: '请求编号：{{requestId}}',
  },
};
