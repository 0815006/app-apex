const EMP_NO_KEY = 'apex_current_emp_no'
const EMP_NO_REGEX = /^\d{7}$/

/**
 * 获取当前登录员工号
 */
export function getCurrentEmpNo(): string {
  return localStorage.getItem(EMP_NO_KEY) || ''
}

/**
 * 设置当前登录员工号
 */
export function setCurrentEmpNo(empNo: string): void {
  localStorage.setItem(EMP_NO_KEY, empNo)
}

/**
 * 校验员工号是否合法（7位数字）
 */
export function isEmpNoValid(empNo: string): boolean {
  return EMP_NO_REGEX.test(empNo)
}

/**
 * 清除当前员工号
 */
export function clearCurrentEmpNo(): void {
  localStorage.removeItem(EMP_NO_KEY)
}
