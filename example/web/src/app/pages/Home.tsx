import { Link } from "react-router";
import { motion } from "motion/react";
import { posts } from "../data/posts";
import { ChevronRight } from "lucide-react";

export function Home() {
  const sortedPosts = [...posts].sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  );
  const featuredPost = posts.find((post) => post.featured) ?? sortedPosts[0];

  return (
    <div className="min-h-screen">
      {/* Hero Section */}
      <section className="max-w-6xl mx-auto px-6 py-24">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="max-w-3xl"
        >
          <h1 className="text-6xl mb-6 text-gray-900">
            设计，创造，
            <br />
            思考未来
          </h1>
          <p className="text-xl text-gray-600 leading-relaxed">
            探索设计、技术和创造力的交汇点。
            <br />
            分享关于用户体验、产品设计和数字创新的见解。
          </p>
        </motion.div>
      </section>

      {/* Featured Post */}
      {featuredPost && (
        <section className="max-w-6xl mx-auto px-6 py-16">
          <motion.div
            initial={{ opacity: 0, y: 30 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2, duration: 0.8 }}
          >
            <Link to={`/blog/${featuredPost.id}`} className="group">
              <div className="grid md:grid-cols-2 gap-12 items-center">
                <div className="aspect-[4/3] overflow-hidden rounded-3xl bg-gray-100 order-2 md:order-1">
                  <img
                    src={featuredPost.image}
                    alt={featuredPost.title}
                    className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                  />
                </div>
                <div className="space-y-4 order-1 md:order-2">
                  <div className="inline-block px-3 py-1 bg-gray-100 rounded-full text-sm text-gray-700">
                    精选文章
                  </div>
                  <h2 className="text-4xl text-gray-900 group-hover:text-gray-600 transition-colors">
                    {featuredPost.title}
                  </h2>
                  <p className="text-lg text-gray-600 leading-relaxed">
                    {featuredPost.excerpt}
                  </p>
                  <div className="flex items-center gap-3 text-gray-500">
                    <span>{featuredPost.date}</span>
                    <span>·</span>
                    <span>{featuredPost.readTime}</span>
                  </div>
                  <div className="flex items-center gap-2 text-gray-900 pt-4 group-hover:gap-3 transition-all">
                    <span>阅读更多</span>
                    <ChevronRight className="w-5 h-5" />
                  </div>
                </div>
              </div>
            </Link>
          </motion.div>
        </section>
      )}
    </div>
  );
}
