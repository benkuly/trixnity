import React from 'react';
import clsx from 'clsx';
import styles from './styles.module.css';

type FeatureItem = {
  title: string;
  img: React.ComponentType<React.ComponentProps<'img'>>;
  description: JSX.Element;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Multipurpose',
    img: require('@site/static/img/logo.png').default,
    description: (
      <>
        Trixnity can be used to write Matrix clients, bots, appservices and servers.
      </>
    ),
  },
  {
    title: 'Multiplatform',
    img: require('@site/static/img/logo.png').default,
    description: (
      <>
        Trixnity can be used on JVM, JS and native targets thanks to Kotlin Multiplatform.
      </>
    ),
  },
  {
    title: 'Extensible',
    img: require('@site/static/img/logo.png').default,
    description: (
      <>
        Trixnity can be extended with custom events and custom storage backends.
      </>
    ),
  },
];

function Feature({title, img, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <img className={styles.featureImg} role="img" src={img} />
      </div>
      <div className="text--center padding-horiz--md">
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): JSX.Element {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}