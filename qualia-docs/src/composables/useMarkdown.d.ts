interface Heading {
  level: number
  text: string
  id: string
}

interface ParsedMarkdown {
  html: string
  title: string
}

export function useMarkdown(): {
  parseMarkdown: (content: string) => ParsedMarkdown
  extractHeadings: (content: string) => Heading[]
  markdownIt: any
}
