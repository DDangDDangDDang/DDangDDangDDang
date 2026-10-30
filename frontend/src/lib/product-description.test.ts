import { describe, expect, it } from 'vitest';
import {
  descriptionImageKeys,
  descriptionTextLength,
  insertImage,
  parseDescription,
  removeImage,
  serializeDescription,
  type DescriptionBlock,
} from './product-description';

const A = 'product-images/7/0f8fad5b-d9cb-469f-a165-70867728950e.png';
const B = 'product-images/7/7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg';

describe('parseDescription', () => {
  it('한 줄 전체가 ![](키)인 줄만 이미지로 보고, 글과 번갈아 나눈다', () => {
    expect(parseDescription(`렌즈 깨끗\n  ![](${A})  \n구성품\n\n박스 있음`)).toEqual([
      { type: 'text', text: '렌즈 깨끗' },
      { type: 'image', key: A },
      { type: 'text', text: '구성품\n\n박스 있음' },
    ]);
  });

  it('글 중간의 이미지 문법·HTML·대체 글자가 있는 이미지는 글자로 둔다', () => {
    const description = `앞 ![](${A}) 뒤\n<img src=x onerror=alert(1)>\n![alt](${B})\n![](https://evil.example/a b.png)`;
    expect(parseDescription(description)).toEqual([{ type: 'text', text: description }]);
  });

  it('이미지끼리 붙어 있거나 앞뒤에 글이 없으면 빈 글 덩어리를 둔다', () => {
    expect(parseDescription(`![](${A})\n![](${B})`)).toEqual([
      { type: 'text', text: '' },
      { type: 'image', key: A },
      { type: 'text', text: '' },
      { type: 'image', key: B },
      { type: 'text', text: '' },
    ]);
    expect(parseDescription(undefined)).toEqual([{ type: 'text', text: '' }]);
  });
});

describe('serializeDescription', () => {
  it('나눈 덩어리를 다시 이으면 같은 설명이 된다', () => {
    for (const description of [`a\n![](${A})\nb`, `a\n\n![](${A})\n\nb`, `![](${A})\n![](${B})`, '그냥 글', '']) {
      expect(serializeDescription(parseDescription(description))).toBe(description);
    }
  });
});

describe('insertImage / removeImage', () => {
  it('글의 caret 자리에서 나눠 이미지를 넣고, 빼면 다시 잇는다', () => {
    const blocks: DescriptionBlock[] = [{ type: 'text', text: '첫 줄\n둘째 줄' }];
    const inserted = insertImage(blocks, 0, '첫 줄\n'.length, A);
    expect(serializeDescription(inserted)).toBe(`첫 줄\n![](${A})\n둘째 줄`);
    expect(serializeDescription(removeImage(inserted, 1))).toBe('첫 줄\n둘째 줄');
  });

  it('글 덩어리가 아니면 끝에 붙인다', () => {
    expect(serializeDescription(insertImage([{ type: 'text', text: '글' }], 5, 0, A))).toBe(`글\n![](${A})`);
  });
});

describe('descriptionImageKeys / descriptionTextLength', () => {
  it('이미지 키는 중복 없이, 글자 수는 이미지 줄을 빼고 센다', () => {
    const description = `가나\n![](${A})\n다\n![](${A})`;
    expect(descriptionImageKeys(description)).toEqual([A]);
    expect(descriptionTextLength(description)).toBe(3);
  });
});
