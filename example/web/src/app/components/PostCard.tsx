import { Link } from "react-router";
import { motion } from "motion/react";
import type { Post } from "../data/posts";

interface PostCardProps {
  post: Post;
  index: number;
}

export function PostCard({ post, index }: PostCardProps) {
  return (
    <motion.article
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.1 }}
    >
      <Link to={`/blog/${post.id}`} className="group block">
        <div className="aspect-[16/9] overflow-hidden rounded-2xl bg-gray-100 mb-4">
          <img
            src={post.image}
            alt={post.title}
            className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
          />
        </div>

        <div className="space-y-2">
          <div className="flex items-center gap-3 text-sm text-gray-500">
            <span>{post.date}</span>
            <span>·</span>
            <span>{post.readTime}</span>
            <span>·</span>
            <span className="text-gray-700">{post.category}</span>
          </div>

          <h3 className="text-2xl text-gray-900 group-hover:text-gray-600 transition-colors">
            {post.title}
          </h3>

          <p className="text-gray-600 line-clamp-2">{post.excerpt}</p>
        </div>
      </Link>
    </motion.article>
  );
}
