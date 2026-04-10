export interface Post {
  id: string;
  title: string;
  excerpt: string;
  content: string;
  date: string;
  readTime: string;
  category: string;
  image: string;
  featured?: boolean;
}

export const posts: Post[] = [
  {
    id: "1",
    title: "设计系统的力量",
    excerpt: "探索现代设计系统如何改变产品开发流程，提升团队效率和用户体验。",
    content: `
# 设计系统的力量

设计系统不仅仅是一套UI组件库，它是一种思维方式，一种组织设计和开发工作的方法论。

## 为什么需要设计系统

在当今快节奏的产品开发环境中，设计系统已经成为团队协作的基石。它提供了：

- **一致性**：确保产品在所有触点上保持统一的外观和感觉
- **效率**：减少重复工作，让团队专注于解决独特的问题
- **可扩展性**：随着产品的成长，设计系统也能够适应变化

## 构建设计系统的关键要素

### 1. 设计原则

设计原则是设计系统的基础，它们定义了产品的设计哲学和价值观。

### 2. 组件库

可复用的UI组件是设计系统的核心，它们应该是灵活的、可访问的和文档完善的。

### 3. 设计令牌

颜色、字体、间距等设计令牌确保了视觉一致性。

## 实施建议

实施设计系统是一个迭代的过程。从小处着手，逐步完善，始终保持与团队的沟通。

记住，设计系统是为人服务的工具，而不是束缚创造力的枷锁。
    `,
    date: "2026-03-15",
    readTime: "5 分钟",
    category: "设计",
    image: "https://images.unsplash.com/photo-1625461291092-13d0c45608b3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxtaW5pbWFsJTIwbW9kZXJuJTIwZGVzayUyMHdvcmtzcGFjZXxlbnwxfHx8fDE3NzU4MzcyOTN8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
    featured: true,
  },
  {
    id: "2",
    title: "极简主义的美学",
    excerpt: "深入了解极简主义设计哲学，以及如何在数字产品中应用这一理念。",
    content: `
# 极简主义的美学

极简主义不是简单的减法，而是一种深思熟虑的设计方法。

## 少即是多

在设计中，我们常常面临着添加更多功能的诱惑。但真正的艺术在于知道何时停止。

极简主义要求我们：

- 删除不必要的元素
- 强调核心功能
- 创造清晰的视觉层次

## 空白的力量

空白（或负空间）不是浪费的空间，而是设计的重要组成部分。它帮助：

1. 引导用户注意力
2. 提高可读性
3. 创造优雅的视觉体验

## 实践中的极简主义

Apple 的产品设计是极简主义的典范。每一个元素都有其存在的理由，没有多余的装饰。

在您的下一个项目中，尝试问自己："这个元素真的必要吗？"如果答案不是明确的"是"，那么考虑删除它。
    `,
    date: "2026-03-20",
    readTime: "4 分钟",
    category: "设计",
    image: "https://images.unsplash.com/photo-1769704552351-d5059b8bb6f9?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxhYnN0cmFjdCUyMGdyYWRpZW50JTIwYmx1ZSUyMHB1cnBsZXxlbnwxfHx8fDE3NzU3NTU2MDV8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
  },
  {
    id: "3",
    title: "用户体验的未来",
    excerpt: "展望未来十年用户体验设计的发展趋势和新兴技术的影响。",
    content: `
# 用户体验的未来

技术的快速发展正在重塑我们设计和思考用户体验的方式。

## 新兴趋势

### AI 驱动的个性化

人工智能正在使我们能够为每个用户创造独特的体验。但这也带来了新的挑战：如何在个性化和隐私之间找到平衡？

### 语音和手势交互

随着智能助手和AR/VR技术的普及，我们需要超越传统的屏幕界面思考。

### 可持续设计

数字产品也有碳足迹。未来的设计师需要考虑其作品的环境影响。

## 不变的核心

尽管技术在变化，但用户体验的核心原则保持不变：

- 以用户为中心
- 可访问性
- 简单性

## 准备未来

作为设计师，我们需要保持学习和适应的能力。但同时，也要记住设计的根本目的：创造有用、可用和令人愉悦的体验。
    `,
    date: "2026-04-01",
    readTime: "6 分钟",
    category: "技术",
    image: "https://images.unsplash.com/photo-1772408186777-7bbc603c4fb3?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxlbGVnYW50JTIwbm90ZWJvb2slMjBjb2ZmZWV8ZW58MXx8fHwxNzc1ODM3MjkzfDA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
  },
  {
    id: "4",
    title: "创造力与约束",
    excerpt: "为什么限制有时候反而能激发更大的创造力。",
    content: `
# 创造力与约束

我们常常认为自由是创造力的前提，但实际上，适当的约束往往能激发更大的创新。

## 约束的力量

当我们面对无限的可能性时，往往会感到不知所措。约束为我们提供了：

- 清晰的方向
- 专注的焦点
- 创新的动力

## 设计中的约束

在设计系统中工作时，我们总是在规则和自由之间寻找平衡。最好的设计系统提供了足够的结构来确保一致性，同时也留有创新的空间。

## 拥抱限制

下次当您遇到限制时，不要将其视为障碍，而是将其看作是创新的机会。

Twitter 最初的 140 字符限制就是一个完美的例子——这个约束定义了整个平台的特色，并激发了无数创意的表达方式。
    `,
    date: "2026-04-05",
    readTime: "4 分钟",
    category: "思考",
    image: "https://images.unsplash.com/photo-1512428813834-c702c7702b78?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxtaW5pbWFsaXN0JTIwb2ZmaWNlJTIwcGxhbnR8ZW58MXx8fHwxNzc1ODM3Mjk0fDA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
  },
  {
    id: "5",
    title: "现代Web的设计原则",
    excerpt: "构建现代Web应用时需要遵循的核心设计和开发原则。",
    content: `
# 现代Web的设计原则

Web设计正在经历一场革命。响应式设计、性能优化和可访问性不再是可选项，而是必需品。

## 核心原则

### 1. 移动优先

超过60%的Web流量来自移动设备。从小屏幕开始设计，然后逐步增强。

### 2. 性能至上

每一毫秒都很重要。用户期待瞬间加载的体验。

### 3. 可访问性

设计应该为所有人服务，包括残障用户。这不仅是道德责任，也是法律要求。

## 最佳实践

- 使用语义化HTML
- 优化图片和资源
- 实现渐进增强
- 确保键盘导航

## 工具和技术

现代Web开发工具链为我们提供了强大的能力：

- React、Vue等现代框架
- Tailwind CSS等实用工具优先的CSS框架
- Vite等快速构建工具

但记住，工具只是手段，最终目标是创造出色的用户体验。
    `,
    date: "2026-04-08",
    readTime: "5 分钟",
    category: "开发",
    image: "https://images.unsplash.com/photo-1652876256405-3902cc201b22?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxtb2Rlcm4lMjBhcmNoaXRlY3R1cmUlMjBidWlsZGluZ3xlbnwxfHx8fDE3NzU3ODg1MzJ8MA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
  },
  {
    id: "6",
    title: "色彩心理学",
    excerpt: "了解颜色如何影响用户情绪和行为，以及如何在设计中有效使用色彩。",
    content: `
# 色彩心理学

颜色不仅仅是装饰——它们传达情感、引导注意力，甚至影响决策。

## 颜色的意义

不同的颜色唤起不同的情感反应：

- **蓝色**：信任、平静、专业
- **红色**：激情、紧迫、能量
- **绿色**：成长、和谐、健康
- **黄色**：乐观、温暖、注意

但记住，颜色的含义因文化而异。在设计全球产品时，考虑文化差异至关重要。

## 在设计中应用

### 建立视觉层次

使用颜色来引导用户的注意力到最重要的元素。

### 传达品牌个性

您的配色方案应该反映品牌的价值观和个性。

### 确保可读性

始终检查颜色对比度，确保文本清晰可读。

## 实用建议

- 从一个主色开始
- 使用60-30-10规则（60%主色，30%辅助色，10%强调色）
- 考虑色盲用户
- 在不同的设备和光线条件下测试

颜色是设计师最强大的工具之一。明智地使用它。
    `,
    date: "2026-04-10",
    readTime: "4 分钟",
    category: "设计",
    image: "https://images.unsplash.com/photo-1595411425732-e69c1abe2763?crop=entropy&cs=tinysrgb&fit=max&fm=jpg&ixid=M3w3Nzg4Nzd8MHwxfHNlYXJjaHwxfHxhYnN0cmFjdCUyMGdlb21ldHJpYyUyMHBhdHRlcm58ZW58MXx8fHwxNzc1ODM3Mjk0fDA&ixlib=rb-4.1.0&q=80&w=1080&utm_source=figma&utm_medium=referral",
  },
];
