import { ImageWithFallback } from './figma/ImageWithFallback';
import { Calendar } from 'lucide-react';

interface BlogCardProps {
  title: string;
  excerpt: string;
  imageUrl: string;
  category: string;
  date: string;
  readTime: string;
}

export function BlogCard({ title, excerpt, imageUrl, category, date, readTime }: BlogCardProps) {
  return (
    <article className="group cursor-pointer">
      <div className="relative aspect-[4/3] rounded-xl overflow-hidden mb-4 bg-gray-100">
        <ImageWithFallback
          src={imageUrl}
          alt={title}
          className="w-full h-full object-cover transition-transform duration-500 group-hover:scale-105"
        />
      </div>
      <div className="flex items-center gap-2 mb-3">
        <span className="text-xs tracking-wide text-gray-500 uppercase">{category}</span>
        <span className="text-xs text-gray-400">•</span>
        <span className="text-xs text-gray-500">{readTime}</span>
      </div>
      <h3 className="text-xl tracking-tight mb-2 group-hover:text-gray-600 transition-colors">
        {title}
      </h3>
      <p className="text-sm text-gray-600 leading-relaxed mb-3 line-clamp-2">
        {excerpt}
      </p>
      <div className="flex items-center gap-2 text-xs text-gray-500">
        <Calendar className="w-3 h-3" />
        <span>{date}</span>
      </div>
    </article>
  );
}
