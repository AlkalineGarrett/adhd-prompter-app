import { describe, it, expect } from 'vitest'
import { Lexer } from '../../../dsl/language/Lexer'
import { Parser } from '../../../dsl/language/Parser'
import { Executor } from '../../../dsl/runtime/Executor'
import { Environment } from '../../../dsl/runtime/Environment'
import type { DslValue } from '../../../dsl/runtime/DslValue'
import { toDisplayString } from '../../../dsl/runtime/DslValue'

function execute(source: string, env?: Environment): DslValue {
  const tokens = new Lexer(source).tokenize()
  const directive = new Parser(tokens, source).parseDirective()
  return new Executor().execute(directive, env ?? Environment.create())
}

function display(source: string, env?: Environment): string {
  return toDisplayString(execute(source, env))
}

describe('DateFunctions - extended', () => {
  describe('add_days', () => {
    it('should add positive days', () => {
      const result = execute('[add_days(parse_date("2024-01-15"), 5)]')
      expect(result.kind).toBe('DateVal')
      expect(result).toHaveProperty('value', '2024-01-20')
    })

    it('should subtract days with negative number', () => {
      const result = execute('[add_days(parse_date("2024-01-15"), sub(0, 5))]')
      expect(result.kind).toBe('DateVal')
      expect(result).toHaveProperty('value', '2024-01-10')
    })

    it('should handle month boundary', () => {
      const result = execute('[add_days(parse_date("2024-01-30"), 3)]')
      expect(result.kind).toBe('DateVal')
      expect(result).toHaveProperty('value', '2024-02-02')
    })

    it('should handle year boundary', () => {
      const result = execute('[add_days(parse_date("2024-12-30"), 5)]')
      expect(result.kind).toBe('DateVal')
      expect(result).toHaveProperty('value', '2025-01-04')
    })

    it('should throw for non-date first arg', () => {
      expect(() => execute('[add_days("2024-01-15", 5)]')).toThrow()
    })

    it('should throw for non-number second arg', () => {
      expect(() => execute('[add_days(parse_date("2024-01-15"), "5")]')).toThrow()
    })
  })

  describe('diff_days', () => {
    it('should return positive difference', () => {
      const result = execute('[diff_days(parse_date("2024-01-20"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('NumberVal')
      expect(result).toHaveProperty('value', 5)
    })

    it('should return negative difference', () => {
      const result = execute('[diff_days(parse_date("2024-01-15"), parse_date("2024-01-20"))]')
      expect(result.kind).toBe('NumberVal')
      expect(result).toHaveProperty('value', -5)
    })

    it('should return 0 for same date', () => {
      const result = execute('[diff_days(parse_date("2024-01-15"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('NumberVal')
      expect(result).toHaveProperty('value', 0)
    })

    it('should throw for non-date args', () => {
      expect(() => execute('[diff_days("2024-01-15", "2024-01-20")]')).toThrow()
    })
  })

  describe('before', () => {
    it('should return true when first date is earlier', () => {
      const result = execute('[before(parse_date("2024-01-15"), parse_date("2024-01-20"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', true)
    })

    it('should return false when first date is later', () => {
      const result = execute('[before(parse_date("2024-01-20"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', false)
    })

    it('should return false for same date', () => {
      const result = execute('[before(parse_date("2024-01-15"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', false)
    })

    it('should throw for non-date args', () => {
      expect(() => execute('[before("2024-01-15", "2024-01-20")]')).toThrow()
    })
  })

  describe('after', () => {
    it('should return true when first date is later', () => {
      const result = execute('[after(parse_date("2024-01-20"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', true)
    })

    it('should return false when first date is earlier', () => {
      const result = execute('[after(parse_date("2024-01-15"), parse_date("2024-01-20"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', false)
    })

    it('should return false for same date', () => {
      const result = execute('[after(parse_date("2024-01-15"), parse_date("2024-01-15"))]')
      expect(result.kind).toBe('BooleanVal')
      expect(result).toHaveProperty('value', false)
    })
  })

  describe('datetime constructor', () => {
    it('should combine date and time into datetime', () => {
      const env = Environment.create().withMockedTime(new Date(2025, 5, 15, 10, 30, 45))
      const result = execute('[once[datetime(date, time)]]', env)
      expect(result.kind).toBe('DateTimeVal')
      expect(result).toHaveProperty('value', '2025-06-15T10:30:45')
    })

    it('should throw for non-date first arg', () => {
      expect(() => execute('[datetime("2024-01-15", time)]')).toThrow()
    })

    it('should throw for non-time second arg', () => {
      const env = Environment.create().withMockedTime(new Date(2025, 0, 1))
      expect(() => execute('[once[datetime(date, "10:00")]]', env)).toThrow()
    })

    it('should throw for wrong argument count', () => {
      const env = Environment.create().withMockedTime(new Date(2025, 0, 1))
      expect(() => execute('[once[datetime(date)]]', env)).toThrow()
    })
  })

  describe('parse_datetime', () => {
    it('should parse ISO datetime with T separator', () => {
      const result = execute('[parse_datetime("2024-01-15T14:30:00")]')
      expect(result.kind).toBe('DateTimeVal')
      expect(result).toHaveProperty('value', '2024-01-15T14:30:00')
    })

    it('should parse datetime with space separator', () => {
      const result = execute('[parse_datetime("2024-01-15 14:30")]')
      expect(result.kind).toBe('DateTimeVal')
      expect(result).toHaveProperty('value', '2024-01-15T14:30:00')
    })

    it('should parse datetime with seconds', () => {
      const result = execute('[parse_datetime("2024-01-15 14:30:45")]')
      expect(result.kind).toBe('DateTimeVal')
      expect(result).toHaveProperty('value', '2024-01-15T14:30:45')
    })

    it('should throw for invalid format', () => {
      expect(() => execute('[parse_datetime("not a date")]')).toThrow()
    })

    it('should throw for date-only string', () => {
      expect(() => execute('[parse_datetime("2024-01-15")]')).toThrow()
    })

    it('should throw for non-string argument', () => {
      expect(() => execute('[parse_datetime(42)]')).toThrow()
    })
  })
})
