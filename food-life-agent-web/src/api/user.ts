import { request } from './http'
import type { CurrentUser, LoginRequest, LoginResponse } from '../types/user'

export function sendLoginCode(phone: string) {
  return request<boolean>({
    url: '/user-api/code',
    method: 'POST',
    params: { phone },
  })
}

export function login(data: LoginRequest) {
  return request<LoginResponse>({
    url: '/user-api/login',
    method: 'POST',
    data,
  })
}

export function queryCurrentUser() {
  return request<CurrentUser>({
    url: '/user-api/me',
    method: 'GET',
  })
}
