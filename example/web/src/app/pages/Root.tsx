import { Outlet } from "react-router";
import { Navigation } from "../components/Navigation";

export function Root() {
  return (
    <div className="min-h-screen bg-white">
      <Navigation />
      <main className="pt-16">
        <Outlet />
      </main>
      <footer className="border-t border-gray-200 mt-32">
        <div className="max-w-6xl mx-auto px-6 py-12">
          <div className="flex justify-between items-center">
            <p className="text-gray-500">© 2026 思考空间. 保留所有权利。</p>
            <div className="flex gap-6 text-gray-500">
              <a href="#" className="hover:text-gray-900 transition-colors">
                Twitter
              </a>
              <a href="#" className="hover:text-gray-900 transition-colors">
                GitHub
              </a>
              <a href="#" className="hover:text-gray-900 transition-colors">
                Email
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
