export const Max_Retries = 3;

export function getRetryDelay(attempt: number): number {
    const baseDelay = 1000;   

    return baseDelay * Math.pow(2, attempt);
}