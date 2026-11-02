import { ApiError } from './errors';

/**
 * 서버가 발급한 업로드 URL(S3 미리 서명한 URL, 로컬은 서버의 업로드 주소)로 파일을 올린다.
 * 서명에 Content-Type이 들어 있어 파일 형식을 그대로 보내야 한다. 토큰은 붙이지 않는다. (URL 자체가 권한)
 */
export async function uploadToSignedUrl(uploadUrl: string, file: Blob): Promise<void> {
  let response: Response;
  try {
    response = await fetch(uploadUrl, { method: 'PUT', headers: { 'Content-Type': file.type }, body: file });
  } catch {
    throw new ApiError('NETWORK_ERROR', '', 0);
  }
  if (!response.ok) {
    throw new ApiError('UPLOAD_FAILED', '파일을 올리지 못했어요. 다시 시도해 주세요.', response.status);
  }
}
