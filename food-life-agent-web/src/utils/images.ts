const FOOD_IMAGES = [
  'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=80',
  'https://images.unsplash.com/photo-1550547660-d9450f859349?auto=format&fit=crop&w=1200&q=80',
  'https://images.unsplash.com/photo-1543353071-10c8ba85a904?auto=format&fit=crop&w=1200&q=80',
  'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?auto=format&fit=crop&w=1200&q=80',
]

export function foodImage(seed?: number, source?: string) {
  if (source && source.trim().length > 0) {
    return source
  }
  const index = Math.abs(seed ?? 0) % FOOD_IMAGES.length
  return FOOD_IMAGES[index]
}
