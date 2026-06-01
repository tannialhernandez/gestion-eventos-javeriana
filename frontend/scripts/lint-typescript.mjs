import { parse } from '@babel/parser'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'

const sourceRoot = 'src'
const sourceFiles = []

function walk(directory) {
  for (const entry of readdirSync(directory)) {
    const path = join(directory, entry)
    if (statSync(path).isDirectory()) {
      walk(path)
      continue
    }
    if (/\.(ts|tsx)$/.test(path)) {
      sourceFiles.push(path)
    }
  }
}

walk(sourceRoot)

const violations = []

for (const file of sourceFiles) {
  const code = readFileSync(file, 'utf8')

  try {
    parse(code, {
      sourceType: 'module',
      plugins: ['typescript', 'jsx', 'estree'],
    })
  } catch (error) {
    violations.push(`${file}: ${error.message}`)
  }

  if (code.includes('dangerouslySetInnerHTML')) {
    violations.push(`${file}: dangerouslySetInnerHTML is not allowed in the SPA`)
  }
}

if (violations.length > 0) {
  console.error('TypeScript source validation failed:')
  for (const violation of violations) {
    console.error(`- ${violation}`)
  }
  process.exit(1)
}

console.log(`Validated ${sourceFiles.length} TypeScript source files.`)
