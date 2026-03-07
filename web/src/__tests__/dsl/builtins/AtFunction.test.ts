import { describe, it, expect } from 'vitest'
import { Lexer } from '../../../dsl/language/Lexer'
import { Parser } from '../../../dsl/language/Parser'
import { Executor } from '../../../dsl/runtime/Executor'
import { Environment } from '../../../dsl/runtime/Environment'
import type { DslValue, ScheduleVal } from '../../../dsl/runtime/DslValue'
import { toDisplayString, ScheduleFrequency } from '../../../dsl/runtime/DslValue'

function execute(source: string, env?: Environment): DslValue {
  const tokens = new Lexer(source).tokenize()
  const directive = new Parser(tokens, source).parseDirective()
  return new Executor().execute(directive, env ?? Environment.create())
}

describe('at() function', () => {
  it('should create one-time schedule from datetime', () => {
    const result = execute('[at(parse_datetime("2026-03-07 09:00"))]')
    expect(result.kind).toBe('ScheduleVal')
    const sched = result as ScheduleVal
    expect(sched.frequency).toBe(ScheduleFrequency.ONCE)
    expect(sched.atTime).toBe('2026-03-07T09:00:00')
  })

  it('should work with datetime constructor', () => {
    const env = Environment.create().withMockedTime(new Date(2026, 2, 7, 9, 0, 0))
    const result = execute('[once[at(datetime(date, time))]]', env)
    expect(result.kind).toBe('ScheduleVal')
    const sched = result as ScheduleVal
    expect(sched.frequency).toBe(ScheduleFrequency.ONCE)
  })

  it('should display one-time schedule', () => {
    const result = execute('[at(parse_datetime("2026-03-07 09:00"))]')
    expect(toDisplayString(result)).toBe('[Schedule: once at 2026-03-07T09:00:00]')
  })

  it('should throw for non-datetime argument', () => {
    expect(() => execute('[at("2026-03-07")]')).toThrow()
  })

  it('should throw for date argument', () => {
    expect(() => execute('[at(parse_date("2026-03-07"))]')).toThrow()
  })

  it('should work in schedule context', () => {
    const result = execute('[schedule(at(parse_datetime("2026-03-07 09:00")), [add(1, 2)])]')
    expect(result.kind).toBe('ScheduleVal')
    const sched = result as ScheduleVal
    expect(sched.frequency).toBe(ScheduleFrequency.ONCE)
  })
})
