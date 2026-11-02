/*
 * 상품 설명 속 이미지. 설명은 글자 그대로 저장·표시하고, 한 줄 전체가 "![](키)"인 줄만 그 자리의 이미지로 본다.
 * HTML·링크·다른 마크다운은 해석하지 않는다. (React가 글자로 그려 XSS가 생기지 않는다)
 * 서버(ProductImageResolver.descriptionImageKeys)와 같은 규칙이다.
 */

/** 앞뒤 공백을 뺀 한 줄 전체가 이 모양이면 이미지 줄 */
const IMAGE_LINE = /^!\[\]\(([^()\s]+)\)$/;

export type DescriptionBlock = { type: 'text'; text: string } | { type: 'image'; key: string };

/** 설명 속 이미지는 20장까지 (서버와 같다) */
export const DESCRIPTION_IMAGE_MAX_COUNT = 20;

export function imageLine(key: string): string {
  return `![](${key})`;
}

/**
 * 설명을 글·이미지 덩어리로 나눈다. 항상 글로 시작하고 글로 끝나며, 글과 이미지가 번갈아 온다. (빈 글일 수 있다)
 * 편집기는 글 덩어리마다 입력칸을 하나씩 그린다.
 */
export function parseDescription(description: string | undefined): DescriptionBlock[] {
  const blocks: DescriptionBlock[] = [];
  let lines: string[] = [];
  const flushText = () => {
    blocks.push({ type: 'text', text: lines.join('\n') });
    lines = [];
  };
  for (const line of (description ?? '').split(/\r\n|\r|\n/)) {
    const match = IMAGE_LINE.exec(line.trim());
    if (match) {
      flushText();
      blocks.push({ type: 'image', key: match[1] ?? '' });
    } else {
      lines.push(line);
    }
  }
  flushText();
  return blocks;
}

/** parseDescription의 반대. 빈 글 덩어리는 빼고, 덩어리마다 줄을 나눠 잇는다. */
export function serializeDescription(blocks: DescriptionBlock[]): string {
  return blocks
    .filter((block) => block.type === 'image' || block.text !== '')
    .map((block) => (block.type === 'image' ? imageLine(block.key) : block.text))
    .join('\n');
}

/** 설명 속 이미지 키 (순서대로, 중복 없이) */
export function descriptionImageKeys(description: string | undefined): string[] {
  const keys = parseDescription(description).flatMap((block) => (block.type === 'image' ? [block.key] : []));
  return [...new Set(keys)];
}

/** 설명 속 이미지 줄 수. 같은 이미지를 여러 줄 넣으면 줄마다 그려지므로 그만큼 센다. (20장 제한, 서버와 같다) */
export function descriptionImageCount(description: string | undefined): number {
  return parseDescription(description).filter((block) => block.type === 'image').length;
}

/** 이미지 줄을 뺀 글자 수 (글자 수 제한·표시용) */
export function descriptionTextLength(description: string | undefined): number {
  return parseDescription(description)
    .flatMap((block) => (block.type === 'text' ? [block.text] : []))
    .join('')
    .trim().length;
}

/**
 * 글 덩어리 textIndex(blocks의 위치)의 caret 자리에 이미지를 넣는다. 그 글은 caret 앞뒤 두 덩어리로 나뉜다.
 * caret 바로 앞뒤의 줄바꿈 하나는 이미지 줄이 대신하므로 뺀다.
 */
export function insertImage(blocks: DescriptionBlock[], textIndex: number, caret: number, key: string): DescriptionBlock[] {
  const block = blocks[textIndex];
  if (!block || block.type !== 'text') return [...blocks, { type: 'image', key }, { type: 'text', text: '' }];
  const before = block.text.slice(0, caret).replace(/\n$/, '');
  const after = block.text.slice(caret).replace(/^\n/, '');
  return [
    ...blocks.slice(0, textIndex),
    { type: 'text', text: before },
    { type: 'image', key },
    { type: 'text', text: after },
    ...blocks.slice(textIndex + 1),
  ];
}

/** imageIndex의 이미지를 빼고, 앞뒤 글 덩어리를 한 줄 띄워 잇는다. */
export function removeImage(blocks: DescriptionBlock[], imageIndex: number): DescriptionBlock[] {
  const prev = blocks[imageIndex - 1];
  const next = blocks[imageIndex + 1];
  if (blocks[imageIndex]?.type !== 'image' || prev?.type !== 'text' || next?.type !== 'text') return blocks;
  const text = [prev.text, next.text].filter((part) => part !== '').join('\n');
  return [...blocks.slice(0, imageIndex - 1), { type: 'text', text }, ...blocks.slice(imageIndex + 2)];
}
