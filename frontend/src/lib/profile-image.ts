/*
 * 프로필 이미지 고르기. 서버(UserService)와 같은 규칙으로 올리기 전에 먼저 걸러 낸다.
 */

/** 서버가 받는 형식 (JPG, PNG, WEBP, GIF) */
export const PROFILE_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;
export const PROFILE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;

/** 올릴 수 없는 파일이면 이유, 올릴 수 있으면 undefined */
export function profileImageProblem(file: { type: string; size: number }): string | undefined {
  if (!(PROFILE_IMAGE_TYPES as readonly string[]).includes(file.type)) return 'JPG, PNG, WEBP, GIF 이미지만 올릴 수 있어요.';
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > PROFILE_IMAGE_MAX_BYTES) return '5MB 이하 이미지만 올릴 수 있어요.';
  return undefined;
}
