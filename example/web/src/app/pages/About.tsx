import { motion } from "motion/react";

export function About() {
  return (
    <div className="min-h-screen">
      <section className="max-w-3xl mx-auto px-6 py-24">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
        >
          <h1 className="text-5xl mb-12 text-gray-900">关于</h1>

          <div className="space-y-8 text-lg text-gray-700 leading-relaxed">
            <p>
              欢迎来到思考空间，这是一个探索设计、技术和创造力交汇点的地方。
            </p>

            <p>
              我相信好的设计不仅仅是关于美学——它是关于解决问题，创造意义，
              并为人们的生活带来积极的影响。在这个博客中，我分享我在设计和
              技术领域的思考、学习和发现。
            </p>

            <h2 className="text-3xl pt-8 text-gray-900">设计哲学</h2>

            <p>
              我的设计方法受到极简主义和以用户为中心的原则的启发。我相信：
            </p>

            <ul className="space-y-4 pl-6">
              <li className="flex gap-3">
                <span className="text-gray-400">•</span>
                <span>简单性是复杂性的最终形式</span>
              </li>
              <li className="flex gap-3">
                <span className="text-gray-400">•</span>
                <span>每个设计决策都应该有其目的</span>
              </li>
              <li className="flex gap-3">
                <span className="text-gray-400">•</span>
                <span>好的设计是看不见的</span>
              </li>
              <li className="flex gap-3">
                <span className="text-gray-400">•</span>
                <span>可访问性和包容性是设计的基础</span>
              </li>
            </ul>

            <h2 className="text-3xl pt-8 text-gray-900">我的工作</h2>

            <p>
              作为一名设计师和开发者，我致力于创造既美观又实用的数字体验。
              我的工作涵盖用户界面设计、设计系统、前端开发等多个领域。
            </p>

            <p>
              当我不在设计和编码时，我喜欢阅读关于设计历史、探索新的创意工具，
              以及思考技术如何塑造我们的未来。
            </p>

            <h2 className="text-3xl pt-8 text-gray-900">联系方式</h2>

            <p>
              如果您想讨论设计、技术或潜在的合作机会，请随时与我联系：
            </p>

            <div className="flex gap-6 pt-4">
              <a
                href="#"
                className="text-gray-900 hover:text-gray-600 transition-colors"
              >
                Twitter
              </a>
              <a
                href="#"
                className="text-gray-900 hover:text-gray-600 transition-colors"
              >
                GitHub
              </a>
              <a
                href="#"
                className="text-gray-900 hover:text-gray-600 transition-colors"
              >
                Email
              </a>
            </div>
          </div>
        </motion.div>
      </section>
    </div>
  );
}
