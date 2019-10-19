const gulp = require('gulp');
const autoprefixer = require('gulp-autoprefixer');
const cleanCSS = require('gulp-clean-css');
const rename = require('gulp-rename');
const sass = require('gulp-sass');
const sourcemaps = require('gulp-sourcemaps');

const browserSync = require('browser-sync');

const paths = {
  'src': {
    'scss': 'src/scss/**/*.scss',
  },
  'dist': {
    'css': 'assets/css/',
  }
};

/* scss */
gulp.task('sass', done => {
  gulp.src(paths.src.scss)
    .pipe(sourcemaps.init())
    .pipe(sass({
      outputStyle: 'expanded',
    }).on('error', sass.logError))
    .pipe(autoprefixer({
      Browserslist: ['last 2 versions'],
    }))
    .pipe(sourcemaps.write())
    .pipe(gulp.dest(paths.dist.css))
    .pipe(cleanCSS())
    .pipe(rename({
      suffix: '.min',
    }))
    .pipe(gulp.dest(paths.dist.css));
  done();
});

/* browser-sync start server*/
gulp.task('browser-sync', done => {
  browserSync.init({ server: './', watch: true, });
  done();
})

// 保存するたびにコンパイルする
gulp.task('dev', () => {
  gulp.watch(paths.src.scss, gulp.task('sass'));
});