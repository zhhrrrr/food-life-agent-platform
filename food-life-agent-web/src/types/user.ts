export interface LoginRequest {
  phone: string
  code: string
}

export interface LoginResponse {
  token: string
}

export interface CurrentUser {
  id: number
  nickName: string
  icon: string
}
