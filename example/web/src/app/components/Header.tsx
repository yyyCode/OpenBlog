import { Menu, X, Search } from 'lucide-react';
import { useState } from 'react';

export function Header() {
  const [isMenuOpen, setIsMenuOpen] = useState(false);

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-white/80 backdrop-blur-xl border-b border-gray-200/50">
      <nav className="max-w-7xl mx-auto px-6 py-4">
        <div className="flex items-center justify-between">
          {/* Logo */}
          <div className="flex items-center">
            <h1 className="text-xl tracking-tight">张伟的博客</h1>
          </div>

          {/* Desktop Navigation */}
          <div className="hidden md:flex items-center gap-8">
            <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
              首页
            </a>
            <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
              文章
            </a>
            <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
              关于
            </a>
            <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
              联系
            </a>
            <button className="p-2 hover:bg-gray-100 rounded-full transition-colors">
              <Search className="w-4 h-4 text-gray-600" />
            </button>
          </div>

          {/* Mobile Menu Button */}
          <button
            onClick={() => setIsMenuOpen(!isMenuOpen)}
            className="md:hidden p-2 hover:bg-gray-100 rounded-full transition-colors"
          >
            {isMenuOpen ? (
              <X className="w-5 h-5 text-gray-600" />
            ) : (
              <Menu className="w-5 h-5 text-gray-600" />
            )}
          </button>
        </div>

        {/* Mobile Menu */}
        {isMenuOpen && (
          <div className="md:hidden mt-4 pt-4 border-t border-gray-200">
            <div className="flex flex-col gap-4">
              <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                首页
              </a>
              <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                文章
              </a>
              <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                关于
              </a>
              <a href="#" className="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                联系
              </a>
            </div>
          </div>
        )}
      </nav>
    </header>
  );
}
