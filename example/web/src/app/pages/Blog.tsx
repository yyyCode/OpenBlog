import { motion } from "motion/react";
import { PostCard } from "../components/PostCard";
import { posts } from "../data/posts";

export function Blog() {
  return (
    <div className="min-h-screen">
      <section className="max-w-6xl mx-auto px-6 py-24">
        <motion.div
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8 }}
          className="mb-16"
        >
          <h1 className="text-5xl mb-6 text-gray-900">所有文章</h1>
          <p className="text-xl text-gray-600 max-w-2xl">
            探索关于设计、技术和创意的深度思考。
          </p>
        </motion.div>

        <div className="grid md:grid-cols-2 gap-x-12 gap-y-16">
          {posts.map((post, index) => (
            <PostCard key={post.id} post={post} index={index} />
          ))}
        </div>
      </section>
    </div>
  );
}
