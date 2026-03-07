/**
 * Exception thrown during DSL execution.
 */
export class ExecutionException extends Error {
  constructor(
    message: string,
    public readonly position?: number,
  ) {
    super(message)
  }
}
