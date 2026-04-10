import { ImageWithFallback } from './figma/ImageWithFallback';
import { ArrowRight } from 'lucide-react';

interface FeaturedPostProps {
  title: string;
  excerpt: string;
  imageUrl: string;
  category: string;
  readTime: string;
}

export function FeaturedPost({ title, excerpt, imageUrl, category, readTime }: FeaturedPostProps) {
  return (
    <article className="group cursor-pointer">
      <div className="relative aspect-[16/9] rounded-2xl overflow-hidden mb-6 bg-gray-100">
        <ImageWithFallback
          src={imageUrl}
          alt={title}
          className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
        />
      </div>
      <div className="flex items-center gap-3 mb-4">
        <span className="text-xs tracking-wide text-gray-500 uppercase">{category}</span>
        <span className="text-xs text-gray-400">•</span>
        <span className="text-xs text-gray-500">{readTime}</span>
      </div>
      <h3 className="text-3xl md:text-4xl tracking-tight mb-4 group-hover:text-gray-600 transition-colors">
        {title}
      </h3>
      <p className="text-gray-600 leading-relaxed mb-6">
        {excerpt}
      </p>
      <div className="flex items-center gap-2 text-sm group-hover:gap-3 transition-all">
        <span>阅读更多</span>
        <ArrowRight className="w-4 h-4" />
      </div>
    </article>
  );
}
