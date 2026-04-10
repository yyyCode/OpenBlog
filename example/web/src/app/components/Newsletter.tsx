import { Mail } from 'lucide-react';

export function Newsletter() {
  return (
    <section className="py-20 px-6">
      <div className="max-w-4xl mx-auto">
        <div className="bg-gray-50 rounded-3xl p-12 md:p-16 text-center">
          <div className="w-12 h-12 bg-gray-900 rounded-full flex items-center justify-center mx-auto mb-6">
            <Mail className="w-5 h-5 text-white" />
          </div>
          <h3 className="text-3xl md:text-4xl tracking-tight mb-4">
            订阅我的博客
          </h3>
          <p className="text-gray-600 mb-8 max-w-md mx-auto">
            获取最新文章和想法，直接发送到您的邮箱。每周精选，无垃圾邮件。
          </p>
          <form className="max-w-md mx-auto flex gap-3">
            <input
              type="email"
              placeholder="输入您的邮箱"
              className="flex-1 px-4 py-3 rounded-full border border-gray-300 focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent text-sm"
            />
            <button
              type="submit"
              className="px-6 py-3 bg-gray-900 text-white rounded-full hover:bg-gray-800 transition-colors text-sm whitespace-nowrap"
            >
              订阅
            </button>
          </form>
        </div>
      </div>
    </section>
  );
}
