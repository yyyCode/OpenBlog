import { useParams, Link, Navigate } from "react-router";
import { motion } from "motion/react";
import { ChevronLeft } from "lucide-react";
import { posts } from "../data/posts";

export function BlogPost() {
  const { id } = useParams();
  const post = posts.find((p) => p.id === id);

  if (!post) {
    return <Navigate to="/blog" replace />;
  }

  return (
    <div className="min-h-screen">
      <article className="max-w-3xl mx-auto px-6 py-24">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
        >
          <Link
            to="/blog"
            className="inline-flex items-center gap-2 text-gray-600 hover:text-gray-900 transition-colors mb-12 group"
          >
            <ChevronLeft className="w-5 h-5 group-hover:-translate-x-1 transition-transform" />
            <span>返回博客</span>
          </Link>

          <div className="mb-8">
            <div className="flex items-center gap-3 text-sm text-gray-500 mb-6">
              <span>{post.date}</span>
              <span>·</span>
              <span>{post.readTime}</span>
              <span>·</span>
              <span className="text-gray-700">{post.category}</span>
            </div>
            <h1 className="text-5xl mb-6 text-gray-900">{post.title}</h1>
            <p className="text-xl text-gray-600">{post.excerpt}</p>
          </div>

          <div className="aspect-[16/9] overflow-hidden rounded-3xl bg-gray-100 mb-12">
            <img
              src={post.image}
              alt={post.title}
              className="w-full h-full object-cover"
            />
          </div>

          <div className="prose prose-lg max-w-none">
            <div
              className="whitespace-pre-wrap text-gray-700 leading-relaxed"
              style={{
                fontSize: "1.125rem",
                lineHeight: "1.75rem",
              }}
            >
              {post.content}
            </div>
          </div>

          <div className="mt-16 pt-8 border-t border-gray-200">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500 mb-1">作者</p>
                <p className="text-gray-900">设计师</p>
              </div>
              <div className="flex gap-4">
                <button className="px-6 py-2 bg-gray-100 text-gray-900 rounded-full hover:bg-gray-200 transition-colors">
                  分享
                </button>
              </div>
            </div>
          </div>
        </motion.div>
      </article>

      {/* Related Posts */}
      <section className="max-w-6xl mx-auto px-6 py-24 border-t border-gray-200">
        <h2 className="text-3xl mb-12 text-gray-900">相关文章</h2>
        <div className="grid md:grid-cols-3 gap-12">
          {posts
            .filter((p) => p.id !== post.id)
            .slice(0, 3)
            .map((relatedPost, index) => (
              <motion.article
                key={relatedPost.id}
                initial={{ opacity: 0, y: 20 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ delay: index * 0.1 }}
              >
                <Link to={`/blog/${relatedPost.id}`} className="group block">
                  <div className="aspect-[16/9] overflow-hidden rounded-2xl bg-gray-100 mb-4">
                    <img
                      src={relatedPost.image}
                      alt={relatedPost.title}
                      className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                  </div>
                  <h3 className="text-xl text-gray-900 group-hover:text-gray-600 transition-colors mb-2">
                    {relatedPost.title}
                  </h3>
                  <p className="text-gray-600 text-sm">{relatedPost.excerpt}</p>
                </Link>
              </motion.article>
            ))}
        </div>
      </section>
    </div>
  );
}
